package tf.user_profile.module

import com.google.inject.name.Named
import com.google.inject.{Inject, Provides, Singleton}
import com.twitter.finagle.thrift
import com.twitter.inject.TwitterModule
import tf.user_profile.repository.SSDBKeyValueRepository.KeyValueRepositoryAsync
import tf.user_profile.repository.TokenCodeRepository
import tf.user_profile.service.accountkit.ConfirmData
import tf.user_profile.service.verification.EmailChannelService
import tf.user_profile.service._
import tf.user_profile.util.ZConfig
import org.nutz.ssdb4j.SSDBs
import org.nutz.ssdb4j.spi.SSDB
import tf.user_profile.repository.{KeyValueRepository, QuotaRepository, TokenCodeRepository, UserProfileRepository, UserProfileRepositoryImpl}
import tf.user_profile.service.accountkit.{AccountKitClient, AccountKitPersistentStorage, ConfirmData}
import tf.user_profile.service.verification.{ChannelService, EmailChannelService, EmailVerifyService, VerificationConfig, VerifyService}
import tf.user_profile.service.{AuthService, AuthServiceImpl, AuthorizationService, AuthorizationServiceImpl, CaasService, CaasServiceImpl, UserProfileService, UserProfileServiceImpl}
import tf.user_profile.util.{JsonParser, ZConfig}
import user_caas.service.TCaasService

/**
 * @author anhlt
 */
object UserProfileModuleTestImpl extends TwitterModule {


}

object UserProfileModule extends TwitterModule {

  override def singletonStartup(injector: com.twitter.inject.Injector): Unit = {
    super.singletonStartup(injector)
  }

  @Singleton
  @Provides
  @Named("EnableSendSmsToPhone")
  def providesEnableSendSmsToPhoneConfig(): Boolean = ZConfig.getBoolean("sms.enable", default = true)

  @Singleton
  @Provides
  @Named("ssdb_profile")
  def providesProfileSSDB():SSDB = {
    SSDBs.pool(
      ZConfig.getString("db.ssdb.user_profile.host"),
      ZConfig.getInt("db.ssdb.user_profile.port"),
      ZConfig.getInt("db.ssdb.user_profile.timeout_in_ms"), null)
  }

  @Singleton
  @Provides
  @Named("ssdb")
  def providesQuotaSSDB():SSDB = {
    SSDBs.pool(
      ZConfig.getString("db.ssdb.verify_code_email.host"),
      ZConfig.getInt("db.ssdb.verify_code_email.port"),
      ZConfig.getInt("db.ssdb.verify_code_email.timeout_in_ms"), null)
  }

  @Singleton
  @Provides
  def providesCaasService: CaasService = {
    com.twitter.util.Duration
    import com.twitter.finagle.Thrift
    import com.twitter.finagle.service.{Backoff, RetryBudget}
    import com.twitter.util.Duration

    val host = ZConfig.getString("caas.thrift.host")
    val port = ZConfig.getInt("caas.thrift.port")
    val timeoutInSecs = ZConfig.getInt("caas.thrift.timeout_in_secs",5)
    val label = "user-caas-client-from-userprofile"

    val client = Thrift.client
      .withRequestTimeout(Duration.fromSeconds(timeoutInSecs))
      .withSessionPool.minSize(1)
      .withSessionPool.maxSize(10)
      .withRetryBudget(RetryBudget())
      .withRetryBackoff(Backoff.exponentialJittered(Duration.fromSeconds(5), Duration.fromSeconds(32)))
      .withClientId(thrift.ClientId(label))
      .build[TCaasService.MethodPerEndpoint](s"$host:$port", label)

    CaasServiceImpl(client)
  }

  @Singleton
  @Provides
  def providesUserProfileRepository(@Inject @Named("ssdb_profile") ssdb: SSDB): UserProfileRepository = {
    val usernameKey = ZConfig.getString("db.ssdb.user_profile.username_key")
    val emailKey = ZConfig.getString("db.ssdb.user_profile.email_key")
    val phoneNumberKey = ZConfig.getString("db.ssdb.user_profile.phone_number_key")

    UserProfileRepositoryImpl(ssdb,
      usernameKey,
      emailKey,
      phoneNumberKey)
  }

  @Singleton
  @Provides
  def providesUserProfileService(@Inject caasService: CaasService,
                                 profileRepository: UserProfileRepository): UserProfileService = {

    UserProfileServiceImpl(caasService, profileRepository)
  }

  @Singleton
  @Provides
  def providesAuthenService(@Inject
                            phoneAccountService: VerifyService,
                            caasService: CaasService,
                            userProfileService: UserProfileService): AuthService = {
    new AuthServiceImpl(
      phoneAccountService,
      caasService,
      userProfileService
    )
  }

  @Singleton
  @Provides
  def providesAuthorService(@Inject
                            caasService: CaasService,
                            userProfileService: UserProfileService): AuthorizationService = {
    new AuthorizationServiceImpl(caasService, userProfileService)
  }


  @Singleton
  @Provides
  @Named("quota_repo")
  def providesPhoneQuotaRepo(@Inject @Named("ssdb") ssdb: SSDB): KeyValueRepository[String, Int] = {
    new QuotaRepository(ssdb)
  }

  @Singleton
  @Provides
  @Named("token_code_repo")
  def providesTokenCodeRepo(@Named("ssdb") ssdb: SSDB): KeyValueRepository[String, String] = {
    new TokenCodeRepository(ssdb)
  }


  @Singleton
  @Provides
  def providesAccountKitClient(@Inject
                               @Named("token_code_repo") tokenPhoneRepo: KeyValueRepository[String, String]): AccountKitClient = {
    AccountKitClient(
      appId = ZConfig.getString("db.accountkit.app_id"),
      appSecret = ZConfig.getString("db.accountkit.app_secret"),
      redirectUrl = ZConfig.getString("db.accountkit.redirect_url"),
      regionDefault = ZConfig.getString("db.accountkit.region_default"),
      new AccountKitPersistentStorage {

        val verifyCodeExpireTimeInSecond: Int = ZConfig.getInt("phone_verify_service.code_expire_time_in_second", 3 * 60)

        override def set(countryCode: String, phoneNumber: String, data: ConfirmData): Unit = {
          val verifyCodeKey = buildVerifyCodeKey(countryCode, phoneNumber)
          tokenPhoneRepo.put(verifyCodeKey, JsonParser.toJson(data, false))
          tokenPhoneRepo.asyncExpire(verifyCodeKey, verifyCodeExpireTimeInSecond).onFailure(ex => {
            tokenPhoneRepo.asyncDelete(verifyCodeKey)
            throw ex
          })
        }

        override def get(countryCode: String, phoneNumber: String): Option[ConfirmData] = {
          tokenPhoneRepo.get(buildVerifyCodeKey(countryCode, phoneNumber)).filter(_.nonEmpty).map(JsonParser.fromJson[ConfirmData])
        }

        override def remove(countryCode: String, phoneNumber: String): Unit = {
          tokenPhoneRepo.delete(buildVerifyCodeKey(countryCode, phoneNumber))
        }

        def buildVerifyCodeKey(countryCode: String, phoneNumber: String): String = s"verifyCode-$countryCode$phoneNumber"
      }
    )
  }


  @Singleton
  @Provides
  def providesChannelService(): ChannelService = {
    val host = ZConfig.getString("verification.email.server.host")
    val port = ZConfig.getInt("verification.email.server.port")
    val username = ZConfig.getString("verification.email.server.username")
    val password = ZConfig.getString("verification.email.server.password")
    EmailChannelService(host, port, username, password)
  }

  @Singleton
  @Provides
  def providesVerifyService(@Inject
                            @Named("token_code_repo") tokenRepo: KeyValueRepository[String, String],
                            @Named("quota_repo") quotaRepo: KeyValueRepository[String, Int],
                            channel: ChannelService): VerifyService = {

    val verificationCodeTemplate = ZConfig.getString("verification.email.message_template")
    val forgotPasswordCodeTemplate = ZConfig.getString("verification.email.forgot_password_message_template")
    val defaultTestCode = ZConfig.getString("verification.email.default_test_code", "1234")
    val expireTimeInSecond = ZConfig.getInt("verification.email.code_expire_time_in_second", 3 * 60)
    val quota = ZConfig.getInt("verification.email.limit_quota", 6)
    val quotaCountdown = ZConfig.getInt("verification.email.limit_countdown_in_second", 900)

    val verificationConfig = VerificationConfig(
      verificationCodeTemplate,
      forgotPasswordCodeTemplate,
      defaultTestCode,
      expireTimeInSecond,
      quota,
      quotaCountdown)

    EmailVerifyService(tokenRepo,quotaRepo, channel, verificationConfig)
  }


}
