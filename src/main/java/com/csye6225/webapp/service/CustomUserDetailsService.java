package com.csye6225.webapp.service;

import com.csye6225.webapp.exception.DatabaseAuthenticationException;
import com.csye6225.webapp.model.User;
import com.csye6225.webapp.repository.UserRepository;
import com.timgroup.statsd.StatsDClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StatsDClient statsDClient;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            // Start timer for database query
            long start = System.currentTimeMillis();

            User user = userRepository.findByEmail(email);

            // Record execution time
            long duration = System.currentTimeMillis() - start;
            statsDClient.recordExecutionTime("db.userRepository.findByEmail.time", duration);

            if (user == null) {
                throw new UsernameNotFoundException("Invalid Email or password");
            }
            return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), new ArrayList<>());
        } catch (DataAccessResourceFailureException e) {
            throw new DatabaseAuthenticationException("Database is currently unavailable", e);
        }
    }
}
