package com.aberdeen_rocketry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;

import com.aberdeen_rocketry.GitHubUploader;
import com.aberdeen_rocketry.Unzipper;



public class Main {

    public static void main(String[] args) throws Exception {

        if(args.length < 1){
            System.out.println("Please specify action: \"pull\" or \"push\"");
            return;
        }

        int i = 0;
        String configPath = null;

        for (String arg : args) {
            if(arg.equals("-c") || arg.equals("--config"))
            {
                if(i >= args.length || args[i+1].startsWith("-")){
                    System.out.println("Please specify a config path with -c /some/config.txt");
                    return;
                }

                configPath = args[i+1];
            }
            i++;
        }

        if(configPath == null){
            System.out.println("Please specify a config path with -c /some/config.txt");
            return;
        }

        Path configFilePath = Paths.get(configPath);

        if(args[0].contains("pull")){
            downlaod(configFilePath);
            return;
        }

        if(args[0].contains("push")){
            GitHubUploader.upload(configFilePath);
            return;
        }

        System.out.println("Unknown action: " + args[0]);
        return;

    }

    private static void downlaod(Path configFile) throws Exception{
        // Load config
        Properties props = new Properties();
        try (FileReader reader = new FileReader(configFile.toString())) {
            props.load(reader);
        }

        String owner = props.getProperty("owner");
        String repo = props.getProperty("repo");
        String branch = props.getProperty("branch");
        String token = props.getProperty("token");
        String folder = props.getProperty("folder");

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER) // we handle redirect manually
            .build();

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/zipball/" + branch;
        HttpRequest initialRequest = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", "token " + token)
            .build();

        HttpResponse<Void> initialResponse = client.send(initialRequest, HttpResponse.BodyHandlers.discarding());
        if (initialResponse.statusCode() == 302) {
            String redirectUrl = initialResponse.headers().firstValue("Location").orElseThrow();
            System.out.println("Redirecting to actual ZIP URL: " + redirectUrl);

            HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(redirectUrl))
                .build();

            HttpResponse<InputStream> zipResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());

            Path outputPath = Paths.get("repo.zip");
            Files.copy(zipResponse.body(), outputPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Downloaded zip to: " + outputPath.toAbsolutePath());

        } else {
            System.out.println("Unexpected status code: " + initialResponse.statusCode());
        }


        String repoRoot = Paths.get(folder).toString();
        String repoCompareRoot =  Paths.get(folder).toString() + "_COMPARE";

        deleteFolder(repoRoot);
        deleteFolder(repoCompareRoot);


        Unzipper.unzip("repo.zip", repoRoot);
        Unzipper.unzip("repo.zip", repoCompareRoot);

    }

    private static void deleteFolder(String path) {

        File dir = new File(path);

        if(!dir.exists())
            return;

        System.out.println("Deleting existing folder" + path);

        String[] entries = dir.list();
        for (String s : entries) {
            File currentFile = new File(dir.getPath(), s);
            currentFile.delete();
        }
    }
}

