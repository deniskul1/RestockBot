package epikjamer.sneakerbot.discord.listeners;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class EventListeners extends ListenerAdapter {

    private final Map<Long, Consumer<MessageReceivedEvent>> awaitingResponses = new HashMap<>();
    private final Map<Long, WebDriver> activeSessions = new HashMap<>();
    private final ExecutorService executorService;

    public EventListeners() {
        this.executorService = Executors.newFixedThreadPool(100);
    }


    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();

        if (message.equalsIgnoreCase("Stop")) {
            WebDriver driver = activeSessions.remove(event.getAuthor().getIdLong());
            if (driver != null) {
                driver.quit();
                event.getChannel().sendMessage("Your current stock check has been stopped.").queue();
            } else {
                event.getChannel().sendMessage("You don't have an active stock check.").queue();
            }
            return;
        }

        Consumer<MessageReceivedEvent> responseHandler = awaitingResponses.remove(event.getAuthor().getIdLong());
        if (responseHandler != null) {
            responseHandler.accept(event);
            return;
        }

        if (message.equalsIgnoreCase("!checkstock")) {
            if (activeSessions.containsKey(event.getAuthor().getIdLong())) {
                event.getChannel().sendMessage("You already have an active stock check. Please type 'Stop' to end it before starting another.").queue();
                return;
            }

            event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the number corresponding to the website you want to check the stock on:\n1: Footlocker CA\n2: Footlocker US\n3: Champs Sports\n4: JD Sports CA\n5: Sport Check \n6: Size.ca")).queue();
            Consumer<MessageReceivedEvent> siteConsumer = siteEvent -> {
                String siteNumber = siteEvent.getMessage().getContentRaw();
                String url = getWebsiteFromNumber(siteNumber);
                if (url != null) {
                    event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the name of the item to check the stock of.")).queue();
                    Consumer<MessageReceivedEvent> itemConsumer = itemEvent -> {
                        String itemName = itemEvent.getMessage().getContentRaw();
                        event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the price for the item EX: $150. If you are unsure what the price of the item is, please search up its retail price")).queue();
                        Consumer<MessageReceivedEvent> priceConsumer = priceEvent -> {
                            String priceRange = priceEvent.getMessage().getContentRaw();
                            event.getChannel().sendMessage(MessageCreateData.fromContent("Now checking, you will be pinged once your item is in stock...")).queue();
                            executorService.submit(() -> {
                                try {
                                    navigateAndCheckStock(url, itemName, priceRange, event.getChannel(), event.getAuthor().getIdLong());
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        };
                        awaitingResponses.put(itemEvent.getAuthor().getIdLong(), priceConsumer);
                    };
                    awaitingResponses.put(siteEvent.getAuthor().getIdLong(), itemConsumer);
                } else {
                    event.getChannel().sendMessage(MessageCreateData.fromContent("Invalid website number. Please try again with a valid number.")).queue();
                }
            };
            awaitingResponses.put(event.getAuthor().getIdLong(), siteConsumer);
        }
    }

    private String getWebsiteFromNumber(String number) {
        switch (number) {
            case "1":
                return "https://www.footlocker.ca";
            case "2":
                return "https://www.footlocker.com";
            case "3":
                return "https://www.champssports.com";
            case "4":
                return "https://jdsports.ca";
            case "5":
                return "https://www.sportchek.ca";
            case "6":
                return "https://size.ca/";
            case "7":
                return "https://www.deadstock.ca/";
            case "8":
                return "https://www.bbbranded.com/";
            case "9":
                return "https://nrml.ca/";
            case "10":
                return "https://lessoneseven.com/";
            default:
                return null;
        }
    }

    private void navigateAndCheckStock(String url, String itemName, String priceRange, MessageChannel channel, long userId) throws Throwable {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\Denis\\Downloads\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-minimized");
        options.addArguments("--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        activeSessions.put(userId, driver);
        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            WebElement searchbar;
            try {
                searchbar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                        "//input[@name='q' or @name='query' or @id='search-input-0' or contains(@class, 'search-menu') or contains(@class, 'modal__toggle-open icon icon-search') or contains(@class, 'icon icon-search')]")));
                searchbar.click();
                searchbar.sendKeys(itemName + Keys.RETURN);
            } catch (TimeoutException e) {
                channel.sendMessage("<@" + userId + "> The search bar could not be found. The website might be blocked or slow to respond, try again later.").queue();
                return;
            }
            boolean inStock = false;
            while (!inStock) {
                try {
                    WebElement itemElement = driver.findElement(By.xpath("//*[contains(text(),'" + priceRange + "')]"));
                    try {
                        itemElement.click(); // Attempt to click on the item
                        Thread.sleep(5000);
                        String itemLink = driver.getCurrentUrl();
                        channel.sendMessage("<@" + userId + "> The item is in stock! Link: " + itemLink).queue();
                    } catch (Exception e) {
                        // If clicking or getting the link fails, notify the user the item is in stock but without a link
                        channel.sendMessage("<@" + userId + "> The item is in stock, but the link could not be grabbed.").queue();
                    }
                    inStock = true;
                } catch (NoSuchElementException | TimeoutException e) {
                    Thread.sleep(10000);
                    driver.navigate().refresh();
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            driver.quit();
            activeSessions.remove(userId);
        }
    }
}
