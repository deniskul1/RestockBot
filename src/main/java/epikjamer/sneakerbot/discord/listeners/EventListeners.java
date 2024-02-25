package epikjamer.sneakerbot.discord.listeners;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class EventListeners extends ListenerAdapter {

    private final Map<Long, Consumer<MessageReceivedEvent>> awaitingResponses = new HashMap<>();
    private final List<String> whitelistedWebsites = Arrays.asList(
            "https://www.sportchek.ca",
            "https://www.champssports.com",
            "https://www.footlocker.com",
            "https://jdsports.ca",
            "https://www.footlocker.ca"
    );
    private final Map<Long, WebDriver> activeSessions = new HashMap<>(); // Track active WebDriver sessions per user
    private final ExecutorService executorService;

    public EventListeners() {
        this.executorService = Executors.newFixedThreadPool(100); // Adjust the pool size as needed
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return; // Ignore bot messages

        String message = event.getMessage().getContentRaw();

        // Check if user wants to stop their current session
        if (message.equalsIgnoreCase("Stop")) {
            WebDriver driver = activeSessions.remove(event.getAuthor().getIdLong());
            if (driver != null) {
                driver.quit(); // Close the browser and end session
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

            event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the URL of the website to check the stock.")).queue();

            Consumer<MessageReceivedEvent> urlConsumer = urlEvent -> {
                String url = urlEvent.getMessage().getContentRaw();
                if (whitelistedWebsites.stream().anyMatch(whitelistedUrl -> url.startsWith(whitelistedUrl))) {
                    event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the name of the item to check the stock of.")).queue();

                    Consumer<MessageReceivedEvent> itemConsumer = itemEvent -> {
                        String itemName = itemEvent.getMessage().getContentRaw();
                        event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the price for the item EX: $150. If you are unsure what the price of the item is, please search up its retail price")).queue();
                        Consumer<MessageReceivedEvent> priceConsumer = priceEvent -> {
                            String priceRange = priceEvent.getMessage().getContentRaw();
                            event.getChannel().sendMessage(MessageCreateData.fromContent("Now checking, you will be pinged once your item is in stock...")).queue();
                            executorService.submit(() -> {
                                try {
                                    navigateAndCheckStock(url, itemName, priceRange, event.getChannel(), Long.parseLong(event.getAuthor().getId()));
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        };

                        awaitingResponses.put(itemEvent.getAuthor().getIdLong(), priceConsumer);
                    };

                    awaitingResponses.put(urlEvent.getAuthor().getIdLong(), itemConsumer);
                } else {
                    event.getChannel().sendMessage(MessageCreateData.fromContent("That website is not whitelisted. Check #current-whitelisted-websites to see which websites are.")).queue();
                }
            };

            awaitingResponses.put(event.getAuthor().getIdLong(), urlConsumer);
        }
    }

    private void navigateAndCheckStock(String url, String itemName, String priceRange, MessageChannel channel, long userId) throws Throwable {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\Denis\\Downloads\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless=new");
        WebDriver driver = new ChromeDriver();
        activeSessions.put(userId, driver); // Track the session
        driver.get(url);
        try {
            Thread.sleep(3000);
            WebElement searchbar = driver.findElement(By.xpath("//input[@name='q' or @name='query' or @id='search-input-0']"));
            Thread.sleep(3000);
            List<WebElement> botMessageElements = driver.findElements(By.xpath("//*[contains(text(),'We want to make sure it is actually you we are dealing with and not a robot.') or contains(text(),'You have been blocked.')]"));
            if (!botMessageElements.isEmpty()) {
                channel.sendMessage("<@" + userId + "> Website is currently blocked, try again later.").queue();
                Thread.sleep(10000);
                driver.quit();
            }
            searchbar.click();
            searchbar.sendKeys(itemName + Keys.RETURN);
            boolean inStock = false;
            while (!inStock) {
                try {
                    Thread.sleep(3000);
                    driver.findElement(By.xpath("//*[contains(text(),'" + priceRange + "')]"));
                    channel.sendMessage("<@" + userId + "> The item is in stock!").queue();
                    inStock = true; // Item found, exit loop
                } catch (NoSuchElementException e) {
                    System.out.println("Not in stock, refreshing...");
                    driver.navigate().refresh();
                    try {
                        Thread.sleep(20000); // Wait before refreshing again
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
                } finally {
                    if (activeSessions.containsKey(userId)) {
                        activeSessions.remove(userId);
                        driver.quit(); // Ensure WebDriver is closed when done
                    }
                }
            }
  }