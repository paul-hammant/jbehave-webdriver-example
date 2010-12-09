package housekeeping

import org.jbehave.core.annotations.BeforeScenario
import org.jbehave.web.selenium.WebDriverProvider
import pages.CartContents
import pages.Home
import pages.Site

class EmptyCartIfNotAlready {

  WebDriverProvider webDriverProvider;

  @BeforeScenario
  def emptyCart() {
    Site site = new Site(webDriverProvider)
    CartContents cartContents = new CartContents(webDriverProvider)
    Home home = new Home(webDriverProvider)

    // In reality you'd prefer to invoke something in the backend to drop the cart.
    // Alternatively, you could identify the cookie that correlates the session, and nuke that
    //
    // As it happens below is interacting with the UI to drop the cart, and made fail-safe
    try {
      home.go()
      if (site.cartSize().equals("1")) {
        cartContents.removeItem()
      }
    } catch (Throwable t) {
      System.out.println("--> emptyCart() failed unexpectedly: " + t.getMessage());
    }
  }
  


}
