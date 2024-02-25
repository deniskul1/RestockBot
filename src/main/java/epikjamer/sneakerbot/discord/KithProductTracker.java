//package epikjamer.sneakerbot.discord;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//import java.io.*;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.HashSet;
//import java.util.Set;
//
//public class KithProductTracker {
//
//    private static final String JSON_URL = "https://kith.com/products.json";
//    private static final String PREVIOUS_STATE_FILE = "previous_products.json";
//
//    public static void main(String[] args) {
//        try {
//            String currentJson = downloadJson(JSON_URL);
//            JSONObject rootObject = new JSONObject(currentJson);
//            JSONArray currentProducts = rootObject.getJSONArray("products");
//
//            JSONArray previousProducts = new JSONArray();
//            try {
//                File file = new File(PREVIOUS_STATE_FILE);
//                if (file.exists()) {
//                    String content = new String(Files.readAllBytes(Paths.get(PREVIOUS_STATE_FILE)));
//                    if (content.trim().startsWith("[")) { // Basic validation to check if it's likely an array
//                        previousProducts = new JSONArray(content);
//                    } else {
//                        System.err.println("Invalid JSON format in previous state file. Resetting to empty array.");
//                        // Optional: Clear the file or handle the error as needed
//                    }
//                }
//            } catch (IOException e) {
//                System.out.println("Error reading previous state file: " + e.getMessage());
//            }
//
//            Set<String> previousIds = new HashSet<>();
//            for (int i = 0; i < previousProducts.length(); i++) {
//                JSONObject product = previousProducts.getJSONObject(i);
//                String id = product.get("id").toString(); // Use toString() to handle non-string ids
//                previousIds.add(id);
//            }
//
//            boolean foundNewProduct = false;
//            for (int i = 0; i < currentProducts.length(); i++) {
//                JSONObject product = currentProducts.getJSONObject(i);
//                String id = product.get("id").toString(); // Use toString() to handle non-string ids
//                if (!previousIds.contains(id)) {
//                    foundNewProduct = true;
//                    System.out.println("New product found: " + product);
//                    // Here, add your handling for new products
//                }
//            }
//
//            if (!foundNewProduct) {
//                System.out.println("No new products found.");
//            }
//
//            try {
//                Files.write(Paths.get(PREVIOUS_STATE_FILE), currentJson.getBytes());
//            } catch (IOException e) {
//                System.out.println("Error writing to previous state file: " + e.getMessage());
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static String downloadJson(String urlString) throws IOException {
//        HttpURLConnection connection = null;
//        try {
//            URL url = new URL(urlString);
//            connection = (HttpURLConnection) url.openConnection();
//            connection.setRequestMethod("GET");
//
//            StringBuilder response = new StringBuilder();
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    response.append(line);
//                }
//            }
//            return response.toString();
//        } finally {
//            if (connection != null) {
//                connection.disconnect();
//            }
//        }
//    }
//}
