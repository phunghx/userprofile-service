package datainsider.user_profile.service.accountkit

import java.net.URI
import java.util.regex.Pattern

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import scala.collection.JavaConversions._

/**
 * @author anhlt
 */
trait ILinkResolver {
  def resolve(baseUrl: String, link: String): String
}

class LinkResolver extends ILinkResolver {
  private val ignorePatterns = Array[String]("^mailto:", "^javascript:", "^#")

  override def resolve(baseUrl: String, input: String): String = {
    var resolvedLink: String = null
    try {
      var inputLink = input
      if (!isIgnore(inputLink)) {
        if (!Pattern.compile("(^http)|(^[/\\\\.])").matcher(inputLink).find)
          inputLink = "/" + inputLink
        val uri = new URI(baseUrl)
        val result = uri.resolve(input)
        //Only get internal url (in the same domain)
        if (uri.getHost.endsWith(result.getHost) || result.getHost.endsWith(uri.getHost))
          resolvedLink = result.toString
      }
    }
    catch {
      case ex: Exception => //Do Nothing
    }
    return resolvedLink
  }

  private def isIgnore(href: String) = {
    var ignoreOK = false
    for (pattern <- ignorePatterns) {
      if (Pattern.compile(pattern).matcher(href).find) {
        ignoreOK = true
      }
    }
    ignoreOK
  }
}

case class HtmlResolver(html: String) {
  private val linkResolver = new LinkResolver
  val document = Jsoup.parse(html)

  def resolve(url: String): HtmlResolver = {
    document.select("*[href] , *[src]").iterator()
      .foreach((e: Element) => {
        val link = if (e.hasAttr("href")) e.attr("href") else e.attr("src")
        val resolvedLink = linkResolver.resolve(url, link)
        if (resolvedLink != null)
          if (e.hasAttr("href"))
            e.attr("href", resolvedLink)
          else
            e.attr("src", resolvedLink)

      })
    this
  }

  def select(cssSelector: String, node: Element = document): Elements = {
    node.select(cssSelector)
  }


}

object HtmlResolver {
  def formParams(cssSelector: String, node: Element): Array[Map[String, String]] = {
    node.select(cssSelector).iterator()
      .map((form: Element) => {
        form.select("input[name] , textarea[name]").iterator()
          .map((e: Element) => {
            val k = e.attr("name")
            val v = if (e.attributes().hasKey("value")) e.attr("value") else e.text()
            k -> v
          }).toMap
      }).toArray
  }
}
