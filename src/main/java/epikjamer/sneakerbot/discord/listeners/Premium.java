package epikjamer.sneakerbot.discord.listeners;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.NoSuchElementException;

import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Premium extends ListenerAdapter {
    private final Map<Long, Consumer<MessageReceivedEvent>> awaitingResponses = new HashMap<>();
    private final List<String> whitelistedWebsites = Arrays.asList("https://www.sportchek.ca", "https://www.champssports.com", "https://www.footlocker.com", "https://jdsports.ca", "https://www.footlocker.ca");
    private final Map<Long, WebDriver> activeSessions = new HashMap<>();
    private final Map<Long, List<WebDriver>> activePremiumSessions = new HashMap<>();
    private final ExecutorService executorService;
    public Premium() {
        this.executorService = Executors.newFixedThreadPool(100);
    }

    public Premium(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        String message = event.getMessage().getContentRaw();
        if (message.equalsIgnoreCase("StopPrem")) {
            List<WebDriver> drivers = activePremiumSessions.remove(event.getAuthor().getIdLong());
            if (drivers != null) {
                drivers.forEach(WebDriver::quit);
                event.getChannel().sendMessage("Your current premium stock checks have been stopped.").queue();
            } else {
                WebDriver driver = activeSessions.remove(event.getAuthor().getIdLong());
                if (driver != null) {
                    driver.quit();
                    event.getChannel().sendMessage("Your current stock check has been stopped.").queue();
                } else {
                    event.getChannel().sendMessage("You don't have an active stock check.").queue();
                }
            }
            return;
        }
        Consumer<MessageReceivedEvent> responseHandler = awaitingResponses.remove(event.getAuthor().getIdLong());
        if (responseHandler != null) {
            responseHandler.accept(event);
            return;
        }
        if (message.equalsIgnoreCase("!checkstockprem")) {
            if (Objects.requireNonNull(event.getMember()).getRoles().stream().noneMatch(role -> role.getName().equalsIgnoreCase("Premium"))) {
                event.getChannel().sendMessage("You need to have the 'Premium' role to use this command.").queue();
                return;
            }
            List<WebDriver> drivers = activePremiumSessions.get(event.getAuthor().getIdLong());
            if (drivers != null && drivers.size() >= 5) {
                event.getChannel().sendMessage("You already have 5 active premium stock checks. Please type 'Stop' to end them before starting another.").queue();
                return;
            }
            event.getChannel().sendMessage(MessageCreateData.fromContent("Please reply with the URL of the website to check the stock.")).queue();
            Consumer<MessageReceivedEvent> urlConsumer = urlEvent -> {
                String url = urlEvent.getMessage().getContentRaw();
                if (whitelistedWebsites.stream().anyMatch(url::startsWith)) {
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
                    awaitingResponses.put(urlEvent.getAuthor().getIdLong(), itemConsumer);
                } else {
                    event.getChannel().sendMessage(MessageCreateData.fromContent("That website is not whitelisted. Check #current-whitelisted-websites to see which websites are.")).queue();
                }
            };
            awaitingResponses.put(event.getAuthor().getIdLong(), urlConsumer);
        }
    }
    private void navigateAndCheckStock(String url, String itemName, String priceRange, MessageChannelUnion channel, long userId) throws Throwable {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\Denis\\Downloads\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless=new");
        WebDriver driver = new ChromeDriver(options);
        List<WebDriver> drivers = activePremiumSessions.computeIfAbsent(userId, k -> new ArrayList<>());
        drivers.add(driver);
        try {
            driver.get(url);
            WebElement searchbar = driver.findElement(By.xpath("//input[@name='q' or @name='query' or @id='search-input-0']"));
            List<WebElement> botMessageElements = driver.findElements(By.xpath("//*[contains(text(),'We want to make sure it is actually you we are dealing with and not a robot.' or 'You have been blocked.')]"));
            try {
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
                        driver.findElement(By.xpath("//*[contains(text(),'" + priceRange + "')]"));
                        channel.sendMessage("<@" + userId + "> The item is in stock!").queue();
                        inStock = true;
                    } catch (NoSuchElementException e) {
                        System.out.println("Not in stock, refreshing...");
                        driver.navigate().refresh();
                        Thread.sleep(20000);
                    }
                }
            } finally {
                if (activePremiumSessions.containsKey(userId)) {
                    drivers.remove(driver);
                    if (drivers.isEmpty()) {
                        activePremiumSessions.remove(userId);
                    }
                    driver.quit();
                }
            }
        } finally {

        }
    }
}
