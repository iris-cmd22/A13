package com.example.db_setup.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.db_setup.Authentication.AuthenticatedUser;
import com.example.db_setup.Authentication.AuthenticatedUserRepository;
import com.example.db_setup.OAuthUserGoogle;
import com.example.db_setup.User;
import com.example.db_setup.UserProfile;
import com.example.db_setup.UserProfileRepository;
import com.example.db_setup.UserRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

// Questa classe è un servizio che gestisce le operazioni relative agli utenti
// è usato per creare un nuovo utente, recuperare un utente esistente e generare un token JWT per l'utente
@Service
public class UserService {

    // Usa la dipendenza UserRepository per accedere ai dati dell'utente sul DB
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserProfile userProfile;
    private NotificationService notificationService;
    // Stessa cosa di sopra
    @Autowired
    private AuthenticatedUserRepository authenticatedUserRepository;
    // Recupera dal DB l'utente con l'email specificata
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    // Crea un nuovo utente con i dettagli forniti da OAuthUserGoogle, recuperati dall'accesso OAuth2
    // e lo salva nel DB
    public User createUserFromOAuth(OAuthUserGoogle oauthUser) {
        User newUser = new User();
        newUser.setEmail(oauthUser.getEmail());
        newUser.setName(oauthUser.getName());
        //Istanzio il profilo
        newUser.setUserProfile(userProfile);
        newUser.getUserProfile().setUser(newUser);

        newUser.setRegisteredWithGoogle(true);
        String[] nameParts = oauthUser.getName().split(" ");
        if (nameParts.length > 1) {
            newUser.setSurname(nameParts[nameParts.length - 1]);
        }
        // Set other user properties as needed

        return userRepository.save(newUser);
    }

    public UserProfile findProfileByEmail(String email) {
        // Recupera l'utente con l'email specificata
        User user = userRepository.findByEmail(email);

        //Controlla se l'utente esiste
        if (user == null) {
            throw new IllegalArgumentException("User with email " + email + " not found");
        }

        // Restituisce il profilo dell'utente
        return user.getUserProfile();
    }

    // Genera un token JWT per l'utente specificato e lo salva nel DB
    public String saveToken(User user) {
        // Genera un token JWT per l'utente
        String token = generateToken(user);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user, token);

        authenticatedUserRepository.save(authenticatedUser);

        return token;
    }
    // Genera un token JWT per l'utente specificato, forse si deve cambiare
    public static String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(1, ChronoUnit.HOURS);
        // usa per generare il token email, data di creazione, data di scadenza, ID utente e ruolo
        String token = Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .claim("userId", user.getID())
                .claim("role", "user")
                .signWith(SignatureAlgorithm.HS256, "mySecretKey")
                .compact();

        return token;
    }

    public void saveProfile(UserProfile userProfile) {
        if (userProfile == null){
            throw new IllegalArgumentException("Profile not found");
        }
        userProfileRepository.save(userProfile);
    }

    //Modifica 04/12/2024 Giuleppe
    public ResponseEntity<?> getStudentiTeam(List<String> idUtenti) {
        System.out.println("Inizio metodo getStudentiTeam. ID ricevuti: " + idUtenti);

            // Controlla se la lista di ID è vuota
        if (idUtenti == null || idUtenti.isEmpty()) {
            System.out.println("La lista degli ID è vuota.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Lista degli ID vuota.");
        }

        try {
        // Converte gli ID in interi
            List<Integer> idIntegerList = idUtenti.stream()
                                                      .map(Integer::valueOf)
                                                      .collect(Collectors.toList());

                // Recupera gli utenti dal database
                List<User> utenti = userRepository.findAllById(idIntegerList);

                // Verifica se sono stati trovati utenti
                if (utenti == null || utenti.isEmpty()) {
                    System.out.println("Nessun utente trovato per gli ID forniti.");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nessun utente trovato.");
                }

                System.out.println("Utenti trovati: " + utenti);

                // Restituisce la lista di utenti trovati
                return ResponseEntity.ok(utenti);

            } catch (NumberFormatException e) {
                System.out.println("Errore durante la conversione degli ID: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body("Formato degli ID non valido. Devono essere numeri interi.");
            } catch (Exception e) {
                System.out.println("Errore durante il recupero degli utenti: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                     .body("Errore interno del server.");
            }
        }

    public ResponseEntity<?> toggleFollow(String UserId, String AuthUserId){

        try{

            //Converto gli id in interi
            Integer userId = Integer.parseInt(UserId);
            Integer authUserId = Integer.parseInt(AuthUserId);

            // Recupero gli utenti dal db
            User autUser = userRepository.findById(authUserId).orElseThrow(() -> new IllegalArgumentException("User not found"));
            User followUser = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

            //Recupera i profili dal db
            Integer autUserProfileId = autUser.getUserProfile().getID();
            Integer followUserProfileId = followUser.getUserProfile().getID();

            //Controlla se l'utente è già seguito
            // Controllo nella li
            boolean wasFollowing = autUser.getUserProfile().getFollowingIds().stream().anyMatch(u -> u.equals(followUserProfileId));

            //Se l'utente è già seguito, lo rimuove dalla lista dei follower
            if(wasFollowing){
                //Unfollow
                followUser.getUserProfile().getFollowerIds().remove(autUserProfileId);
                autUser.getUserProfile().getFollowingIds().remove(followUserProfileId);
            } else {
                //Altrimenti lo aggiunge - Follow
                followUser.getUserProfile().getFollowerIds().add(autUserProfileId);
                //userProfile.getFollowerIds().add(authUserProfile);
                autUser.getUserProfile().getFollowingIds().add(followUserProfileId);
                notificationService.saveNotification(userId, "Hai un nuovo Follower!", autUser.name+" "+autUser.surname+" ha iniziato a seguirti!");
            }

            //Salva le modifiche
            userProfileRepository.save(autUser.getUserProfile());
            userProfileRepository.save(followUser.getUserProfile());

            return ResponseEntity.ok().body(Collections.singletonMap("message","Follow status changed"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error","Internal server error"));
        }
    }

    public List<User> getFollowers(String UserId) {
        Integer userIdInt = Integer.parseInt(UserId);

        // Trova l'utente
        User user = userRepository.findById(userIdInt)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Ottieni gli ID dei following
        List<Integer> followerIds = user.getUserProfile().getFollowerIds();
        System.out.println("Following IDs: " + followerIds);

        if (followerIds == null || followerIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Prova a trovare ogni utente singolarmente per debug
        List<User> followers = new ArrayList<>();
        for (Integer followerId : followerIds) {
            UserProfile userProfile = userProfileRepository.findById(followerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UserProfile not found"));
            User followerUser = userRepository.findByUserProfile(userProfile);
            //Optional<User> followerUser = userRepository.findById(userRepository.findByUserProfile(userProfile).getID());
            if (followerUser != null) {
                followers.add(followerUser);
                System.out.println("Found user with id " + followerId + ": " + followerUser.getName());
            } else {
                System.out.println("User with id " + followerId + " not found");
            }
        }

        System.out.println("Found following: " + followers);
        return followers;
    }

    /*
    public List<User> getFollowing(String UserId){
        Integer userIdInt = Integer.parseInt(UserId);

        User user = userRepository.findById(userIdInt)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Integer> followingIds = user.getUserProfile().getFollowingIds();
        List<User> following = userRepository.findAllById(followingIds);
        System.out.println(following);

        return following;
    }
    */

    public List<User> getFollowing(String UserId) {
        Integer userIdInt = Integer.parseInt(UserId);

        // Trova l'utente
        User user = userRepository.findById(userIdInt)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Ottieni gli ID dei following
        List<Integer> followingIds = user.getUserProfile().getFollowingIds();
        System.out.println("Following IDs: " + followingIds);

        if (followingIds == null || followingIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Prova a trovare ogni utente singolarmente per debug
        List<User> following = new ArrayList<>();
        for (Integer followingId : followingIds) {
            UserProfile userProfile = userProfileRepository.findById(followingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UserProfile not found"));
            User followingUser = userRepository.findByUserProfile(userProfile);
            //Optional<User> followerUser = userRepository.findById(userRepository.findByUserProfile(userProfile).getID());
            if (followingUser != null) {
                following.add(followingUser);
                System.out.println("Found user with id " + followingId + ": " + followingUser.getName());
            } else {
                System.out.println("User with id " + followingId + " not found");
            }
        }

        System.out.println("Found following: " + following);
        return following;
    }

}
