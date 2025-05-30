package com.aberdeen_rocketry;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

public class GitHubUploader {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void upload(Path configFile) throws Exception {
        Properties config = loadConfig(configFile);
        String owner = config.getProperty("owner");
        String repo = config.getProperty("repo");
        String branch = config.getProperty("branch");
        String token = config.getProperty("token");
        String newBranch = "upload-branch-" + System.currentTimeMillis();
        Path localRepo = Paths.get(config.getProperty("folder"));
        Path compareRepo = Paths.get(localRepo.toString() + "_COMPARE");

        String sha = getLatestCommitSha(owner, repo, branch, token);
        createBranch(owner, repo, newBranch, sha, token);

        System.out.println("Created branch: \"" + newBranch + "\", based on branch \"" + branch + "\"");

        boolean changedFiles = false;

        try (Stream<Path> paths = Files.walk(localRepo)) {
            changedFiles = paths.filter(Files::isRegularFile).anyMatch(path -> {
                try {

                    String relativePath = localRepo.relativize(path).toString().replace("\\", "/");
                    String encodedPath = encodePath(relativePath);

                    Path comparePath = Paths.get(compareRepo.toString(), relativePath);

                    return uploadFile(owner, repo, newBranch, path, comparePath, encodedPath, token);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
        }

        if(changedFiles){
            createPullRequest(owner, repo, newBranch, branch, token);
        }
        else {
            System.out.println("No files changed, not creating pull request");
        }
    }

    public static String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8)
                                    .replace("+", "%20")) // Replace '+' with '%20'
                .collect(Collectors.joining("/"));
    }

    private static Properties loadConfig(Path file) throws IOException {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(file.toString())) {
            props.load(reader);
        }
        return props;
    }

    private static String getLatestCommitSha(String owner, String repo, String baseBranch, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/git/ref/heads/" + baseBranch))
                .header("Authorization", "token " + token)
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body()).get("object").get("sha").asText();
    }

    private static void createBranch(String owner, String repo, String newBranch, String sha, String token) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("ref", "refs/heads/" + newBranch);
        body.put("sha", sha);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/git/refs"))
                .header("Authorization", "token " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String getFileSha(String owner, String repo, String branch, Path filePath, String repoPath, String token) throws IOException, InterruptedException{

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + repoPath;
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "token " + token)
                .build();

        HttpResponse<String> response = client.send(getReq, BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode json = mapper.readTree(response.body());
            return json.get("sha").asText();
        }

        System.out.println("Unhappy response: " + response.body());
        return null;

    }

    public static String calculateGitBlobSha(Path path) throws Exception {
        byte[] content = Files.readAllBytes(path);
        String header = "blob " + content.length + "\0";

        // Combine header + content
        byte[] store = new byte[header.length() + content.length];
        System.arraycopy(header.getBytes("UTF-8"), 0, store, 0, header.length());
        System.arraycopy(content, 0, store, header.length(), content.length);

        // SHA-1 digest
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(store);

        // Convert to hex
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    

    private static boolean uploadFile(String owner, String repo, String branch, Path filePath, Path comparePath, String repoPath, String token) throws Exception, IOException, InterruptedException {

        // String sha = getFileSha(owner, repo, branch, filePath, repoPath, token);

        byte[] content = Files.readAllBytes(filePath);
        String encoded = Base64.getEncoder().encodeToString(content);

        ObjectNode body = mapper.createObjectNode();
        body.put("message", "Upload " + repoPath);
        body.put("content", encoded);
        body.put("branch", branch);

        if(Files.exists(comparePath)){
            String oldSha = calculateGitBlobSha(filePath);
            String newSha = calculateGitBlobSha(comparePath);

            if(oldSha.equals(newSha)){
                System.out.println("File not changed: " + repoPath);
                return false;
            }

            body.put("sha", newSha);
            System.out.println("File updated: " + repoPath);
        }
        else{
            System.out.println("New file: " + repoPath);
        }


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + repoPath))
                .header("Authorization", "token " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            System.err.println("Failed to upload: " + repoPath);
            System.err.println(response.body());
        }

        return true;
    }

    private static void createPullRequest(String owner, String repo, String head, String base, String token) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("title", "Automated Pull Request");
        body.put("head", head);
        body.put("base", base);
        body.put("body", "This pull request was created automatically via Java and the GitHub API.");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/pulls"))
                .header("Authorization", "token " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Pull request created: " + mapper.readTree(response.body()).get("html_url").asText());
    }
}
