package com.cargomaze.cargo_maze.controller;

import com.cargomaze.cargo_maze.model.Player;
import com.cargomaze.cargo_maze.model.Position;
import com.cargomaze.cargo_maze.persistance.exceptions.*;
import com.cargomaze.cargo_maze.services.AuthServices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;

import com.cargomaze.cargo_maze.services.CargoMazeServices;
import com.cargomaze.cargo_maze.services.exceptions.CargoMazeServicesException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@RestController
//@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/cargoMaze")
public class CargoMazeController {

    private final CargoMazeServices cargoMazeServices;
    private final AuthServices authServices;

    private static final Logger logger = LoggerFactory.getLogger(CargoMazeController.class);

    @Autowired
    public CargoMazeController(CargoMazeServices cargoMazeServices, AuthServices authServices){
        this.cargoMazeServices = cargoMazeServices;
        this.authServices = authServices;
    }

    @GetMapping("/correct")
    @ResponseBody
    public RedirectView getToken(
            @RegisteredOAuth2AuthorizedClient("aad") OAuth2AuthorizedClient authorizedClient, HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        try {
            String token = authorizedClient.getAccessToken().getTokenValue();
            System.out.println(token);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.microsoft.com/v1.0/me"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> responseGraph = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(responseGraph.body());

            String displayName = jsonNode.path("displayName").asText();
            String userPrincipalName = jsonNode.path("userPrincipalName").asText();

            if (userPrincipalName.isEmpty()) {
                userPrincipalName = authServices.getEmailFromToken(token);
                String[] data = userPrincipalName.split("@");
                displayName = data[0];
            }

//            Map<String, String> responseBody = new HashMap<>();
//            responseBody.put("displayName", displayName);
//            responseBody.put("userPrincipalName", userPrincipalName);
//            responseBody.put("token", token);
//
//            JSONObject json = new JSONObject(responseBody);
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);

//            return new ResponseEntity<>(json.toString(), headers, HttpStatus.OK);

            String redirectUrl = String.format("http://localhost:4200/sessionMenu.html?displayName=%s&userPrincipalName=%s&token=%s",
                URLEncoder.encode(displayName, StandardCharsets.UTF_8),
                URLEncoder.encode(userPrincipalName, StandardCharsets.UTF_8),
                URLEncoder.encode(token, StandardCharsets.UTF_8)
                );
            return new RedirectView(redirectUrl);
        } catch (Exception e) {
            logger.error("Error en el flujo de autenticación", e);
            String errorRedirectUrl = "http://localhost:4200/error.html";
            return new RedirectView(errorRedirectUrl);
        }
    }




    //Session controller

    /**
     * Reurns the base lobby
     * @return
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getGameSession(@PathVariable String id) {
        try {
            return new ResponseEntity<>(cargoMazeServices.getGameSession(id),HttpStatus.OK);
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }


    @GetMapping("/sessions/{id}/board/state")
    public ResponseEntity<?> getBoardState(@PathVariable String id) {
        try {
            return new ResponseEntity<>(cargoMazeServices.getBoardState(id),HttpStatus.ACCEPTED);
        } catch ( CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/sessions/{id}/state")
    public ResponseEntity<?> getGameSessionState(@PathVariable String id){
        try{
            return new ResponseEntity<>(cargoMazeServices.getGameSession(id).getStatus(), HttpStatus.OK);
        } catch (CargoMazePersistanceException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    //Player controller

    @GetMapping("/players/{nickName}")

    public ResponseEntity<?> getPlayer(@PathVariable String nickName) {
        try {
            return new ResponseEntity<>(cargoMazeServices.getPlayerById(nickName),HttpStatus.ACCEPTED);
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/players")
    public ResponseEntity<?> getPlayers() {
        try {
            return new ResponseEntity<>(cargoMazeServices.getPlayers(),HttpStatus.ACCEPTED);
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Creates a new player
     */
    @PostMapping("/players")
    public ResponseEntity<?> createPlayer(@RequestBody Map<String, String> nickname, HttpSession session) {
        try {
            cargoMazeServices.createPlayer(nickname.get("nickname"));
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (CargoMazePersistanceException | CargoMazeServicesException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }


    @GetMapping("/sessions/{id}/players/count")
    public ResponseEntity<?> getPlayerCount(@PathVariable String id) {
        try {
            int playerCount = cargoMazeServices.getPlayerCount(id);
            return new ResponseEntity<>(playerCount, HttpStatus.OK);
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/sessions/{id}/players")
    public ResponseEntity<?> addPlayerToGame(@RequestBody Map<String, String> requestBody, @PathVariable String id) {
        String nickname = requestBody.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Nickname is required and cannot be empty"));
        }
        try {
            cargoMazeServices.addNewPlayerToGame(nickname, id);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Player added to game session", "sessionId", id, "nickname", nickname));
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }
    @GetMapping("/sessions/{id}/players")
    public ResponseEntity<?> getPlayersInSession(@PathVariable String id) {
        try {
            List<Player> players = cargoMazeServices.getPlayersInSession(id);
            return new ResponseEntity<>(players, HttpStatus.OK);
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/sessions/{sessionId}/players/{nickname}/move")
    public ResponseEntity<?> movePlayer(@RequestBody Position position, @PathVariable String sessionId, @PathVariable String nickname) {
        if (position == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "position is required"));
        }
        try {
            if(!cargoMazeServices.move(nickname, sessionId, position)){
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid move"));
            }
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Player moved", "sessionId", sessionId, "nickname", nickname));
        } catch (CargoMazePersistanceException | CargoMazeServicesException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }


    @DeleteMapping("/sessions/{id}/players/{nickname}")
    public ResponseEntity<?> removePlayerFromGame(@PathVariable String id, @PathVariable String nickname) {
        try {
            cargoMazeServices.removePlayerFromGame(nickname, id);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/sessions/{id}/reset")
    public ResponseEntity<?> resetGameSession(@PathVariable String id) {
        try {
            cargoMazeServices.resetGameSession(id);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Game session reset", "sessionId", id));
        } catch (CargoMazePersistanceException | CargoMazeServicesException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/players/{id}")
    public ResponseEntity<?> deletePlayer(@PathVariable String id) {
        try {
            cargoMazeServices.deletePlayer(id);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/players")
    public ResponseEntity<?> deletePlayers() {
        try {
            cargoMazeServices.deletePlayers();
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/session/{sessionId}/players")
    public ResponseEntity<?> removePlayersFromSession(@PathVariable String sessionId) {
        try {
            cargoMazeServices.removePlayersFromSession(sessionId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (CargoMazePersistanceException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }
}