package epikjamer.sneakerbot.discord;

import epikjamer.sneakerbot.discord.listeners.EventListeners;
import epikjamer.sneakerbot.discord.listeners.Premium;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

public class DiscordBot {

    private final ShardManager shardManager;

    public DiscordBot() {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_TOKEN");

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.playing("{Type !checkstock}"));

        shardManager = builder.build();
        shardManager.addEventListener(new EventListeners());
        shardManager.addEventListener(new Premium());
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}

