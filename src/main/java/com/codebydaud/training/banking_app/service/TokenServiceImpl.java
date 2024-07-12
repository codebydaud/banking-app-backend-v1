package com.codebydaud.training.banking_app.service;

import com.codebydaud.training.banking_app.entity.Account;
import com.codebydaud.training.banking_app.entity.Token;
import com.codebydaud.training.banking_app.entity.User;
import com.codebydaud.training.banking_app.exception.InvalidTokenException;
import com.codebydaud.training.banking_app.repository.AccountRepository;
import com.codebydaud.training.banking_app.repository.TokenRepository;
import com.codebydaud.training.banking_app.repository.UserRepository;
import io.jsonwebtoken.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.function.Function;

import static org.springframework.security.core.userdetails.User.withUsername;

@Service
public class TokenServiceImpl implements TokenService {


    private UserRepository userRepository;
    private TokenRepository tokenRepository;
    private AccountRepository accountRepository;

    private final long expiration;
    private final String secret;

    private static final Logger logger = LoggerFactory.getLogger(TokenServiceImpl.class);

    public TokenServiceImpl(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {

        this.secret = secret;
        this.expiration = expiration;
    }

    @Override
    public String getUsernameFromToken(String token) throws InvalidTokenException {
        return getClaimFromToken(token, Claims::getSubject);
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        logger.info("Generating token for user: " + userDetails.getUsername());
        return doGenerateToken(userDetails,
                new Date(System.currentTimeMillis() + expiration));
    }

    @Override
    public String generateToken(UserDetails userDetails, Date expiry) {
        logger.info("Generating token for user: " + userDetails.getUsername());
        return doGenerateToken(userDetails, expiry);
    }

    private String doGenerateToken(UserDetails userDetails, Date expiry) {
        return Jwts.builder().setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(expiry)
                .signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    @Override
    public UserDetails loadUserByUsername(String accountNumber) throws UsernameNotFoundException {
        User user = userRepository.findByAccountAccountNumber(accountNumber)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with account number: " + accountNumber));

        return withUsername(accountNumber).password(user.getPassword()).build();
    }

    @Override
    public Date getExpirationDateFromToken(String token)
            throws InvalidTokenException {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    @Override
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver)
            throws InvalidTokenException {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) throws InvalidTokenException {
        try {
            return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();

        } catch (ExpiredJwtException e) {
            // Delete expired token
            invalidateToken(token);

            throw new InvalidTokenException("Token has expired");

        } catch (UnsupportedJwtException e) {
            throw new InvalidTokenException("Token is not supported");

        } catch (MalformedJwtException e) {
            throw new InvalidTokenException("Token is malformed");

        } catch (SignatureException e) {
            throw new InvalidTokenException("Token signature is invalid");

        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Token is empty");
        }
    }

    @Override
    public void saveToken(String token) throws InvalidTokenException {
        if (tokenRepository.findByToken(token) != null) {
            throw new InvalidTokenException("Token already exists");
        }

        Account account = accountRepository.findByAccountNumber(
                getUsernameFromToken(token));

        logger.info("Saving token for account: " + account.getAccountNumber());

        Token tokenObj = new Token(
                token,
                getExpirationDateFromToken(token),
                account);

        tokenRepository.save(tokenObj);
    }

    @Override
    public void validateToken(String token) throws InvalidTokenException {
        if (tokenRepository.findByToken(token) == null) {
            throw new InvalidTokenException("Token not found");
        }
    }

    @Override
    @Transactional
    public void invalidateToken(String token) {
        if (tokenRepository.findByToken(token) != null) {
            tokenRepository.deleteByToken(token);
        }
    }
}