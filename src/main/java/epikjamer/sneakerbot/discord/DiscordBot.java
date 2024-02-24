package epikjamer.sneakerbot.discord;

import epikjamer.sneakerbot.discord.listeners.EventListeners;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

public class DiscordBot {

    private final ShardManager shardManager;

    public DiscordBot() {
        Dotenv dotenv = Dotenv.load(); // Load environment variables
        String token = dotenv.get("DISCORD_TOKEN"); // Retrieve the bot token from .env file

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES) // Enable GUILD_MESSAGES intent
                .enableIntents(GatewayIntent.DIRECT_MESSAGES) // Enable DIRECT_MESSAGES intent
                .enableIntents(GatewayIntent.MESSAGE_CONTENT) // Enable MESSAGE_CONTENT intent
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.playing("{Type !checkstock}")); // Set a custom activity (optional)

        shardManager = builder.build();
        shardManager.addEventListener(new EventListeners());
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public static void main(String[] args) {
        new DiscordBot(); // Initialize the bot
    }
}

