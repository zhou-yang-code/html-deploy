package com.example.htmldeploy.domain.identity.repository;

import java.util.Optional;

import com.example.htmldeploy.domain.identity.model.Email;
import com.example.htmldeploy.domain.identity.model.UserAccount;
import com.example.htmldeploy.domain.identity.model.UserId;

public interface UserAccountRepository {

    UserAccount save(UserAccount user);

    Optional<UserAccount> findById(UserId id);

    Optional<UserAccount> findByEmail(Email email);
}
