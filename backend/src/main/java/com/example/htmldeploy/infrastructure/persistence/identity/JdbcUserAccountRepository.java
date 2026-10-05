package com.example.htmldeploy.infrastructure.persistence.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.htmldeploy.domain.identity.model.Email;
import com.example.htmldeploy.domain.identity.model.UserAccount;
import com.example.htmldeploy.domain.identity.model.UserId;
import com.example.htmldeploy.domain.identity.model.UserStatus;
import com.example.htmldeploy.domain.identity.repository.UserAccountRepository;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

    private final JdbcClient jdbc;

    public JdbcUserAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserAccount save(UserAccount user) {
        int updated = jdbc.sql("""
                        update user_account
                           set email = :email,
                               password_hash = :passwordHash,
                               status = :status
                         where id = :id
                        """)
                .param("id", user.id().value())
                .param("email", user.email().value())
                .param("passwordHash", user.passwordHash())
                .param("status", user.status().name())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            insert into user_account (id, email, password_hash, status, created_at)
                            values (:id, :email, :passwordHash, :status, :createdAt)
                            """)
                    .param("id", user.id().value())
                    .param("email", user.email().value())
                    .param("passwordHash", user.passwordHash())
                    .param("status", user.status().name())
                    .param("createdAt", user.createdAt())
                    .update();
        }
        return user;
    }

    @Override
    public Optional<UserAccount> findById(UserId id) {
        return jdbc.sql("""
                        select id, email, password_hash, status, created_at
                          from user_account
                         where id = :id
                        """)
                .param("id", id.value())
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<UserAccount> findByEmail(Email email) {
        return jdbc.sql("""
                        select id, email, password_hash, status, created_at
                          from user_account
                         where email = :email
                        """)
                .param("email", email.value())
                .query(this::map)
                .optional();
    }

    private UserAccount map(ResultSet rs, int rowNum) throws SQLException {
        return UserAccount.reconstitute(
                new UserId(uuid(rs, "id")),
                new Email(rs.getString("email")),
                rs.getString("password_hash"),
                UserStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
