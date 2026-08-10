package com.aisocialgame.adminauth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminAuthStore {
    private static final String RECOVERY_SCOPE = "RECOVERY_REBIND_ONLY";
    private final JdbcTemplate jdbc;

    public AdminAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Credential> credential(String subject) {
        return jdbc.query("select encrypted_secret,nonce,key_version,last_accepted_timestep,credential_version "
                        + "from admin_totp_credentials where subject_id=?",
                (rs, row) -> new Credential(rs.getString(1), rs.getBytes(2), rs.getString(3),
                        (Long) rs.getObject(4), rs.getLong(5)), subject).stream().findFirst();
    }

    public boolean credentialExists(String subject) {
        Integer count = jdbc.queryForObject("select count(*) from admin_totp_credentials where subject_id=?",
                Integer.class, subject);
        return count != null && count > 0;
    }

    public void insertCredential(String subject, AdminAuthCrypto.EncryptedValue encrypted,
                                 long acceptedTimestep, Instant now) {
        jdbc.update("insert into admin_totp_credentials(subject_id,encrypted_secret,nonce,key_version,algorithm,digits,"
                        + "period_seconds,last_accepted_timestep,credential_version,enabled_at) values(?,?,?,?,?,?,?,?,?,?)",
                subject, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), "HMAC-SHA1", 6, 30,
                acceptedTimestep, 1L, Timestamp.from(now));
    }

    public long replaceCredential(String subject, AdminAuthCrypto.EncryptedValue encrypted,
                                  long acceptedTimestep, Instant now) {
        jdbc.update("update admin_totp_credentials set encrypted_secret=?,nonce=?,key_version=?,"
                        + "last_accepted_timestep=?,credential_version=credential_version+1,enabled_at=? where subject_id=?",
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), acceptedTimestep,
                Timestamp.from(now), subject);
        Long version = jdbc.queryForObject("select credential_version from admin_totp_credentials where subject_id=?",
                Long.class, subject);
        return version == null ? 0L : version;
    }

    public void rotateCredentialKey(String subject, AdminAuthCrypto.EncryptedValue encrypted) {
        jdbc.update("update admin_totp_credentials set encrypted_secret=?,nonce=?,key_version=? where subject_id=?",
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), subject);
    }

    public boolean acceptTimestep(String subject, long timestep) {
        return jdbc.update("update admin_totp_credentials set last_accepted_timestep=? where subject_id=? "
                        + "and (last_accepted_timestep is null or last_accepted_timestep<?)",
                timestep, subject, timestep) == 1;
    }

    public void insertChallenge(String hash, String subject, String purpose, String sessionHash,
                                Instant passwordAt, AdminAuthCrypto.EncryptedValue pendingSecret,
                                Instant expiresAt) {
        jdbc.update("insert into admin_auth_challenges(challenge_hash,subject_id,purpose,session_hash,password_authenticated_at,"
                        + "encrypted_secret,nonce,key_version,expires_at,attempt_count,created_at) values(?,?,?,?,?,?,?,?,?,?,?)",
                hash, subject, purpose, sessionHash, passwordAt == null ? null : Timestamp.from(passwordAt),
                pendingSecret == null ? null : pendingSecret.ciphertext(),
                pendingSecret == null ? null : pendingSecret.nonce(),
                pendingSecret == null ? null : pendingSecret.keyVersion(), Timestamp.from(expiresAt), 0,
                Timestamp.from(Instant.now()));
    }

    public Optional<Challenge> challenge(String hash) {
        return jdbc.query("select subject_id,purpose,session_hash,password_authenticated_at,encrypted_secret,nonce,key_version,"
                        + "expires_at,attempt_count,consumed_at from admin_auth_challenges where challenge_hash=?",
                (rs, row) -> new Challenge(hash, rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), rs.getString(5),
                        rs.getBytes(6), rs.getString(7), rs.getTimestamp(8).toInstant(), rs.getInt(9),
                        rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant()), hash).stream().findFirst();
    }

    public boolean setPendingSecret(String challengeHash, AdminAuthCrypto.EncryptedValue secret) {
        return jdbc.update("update admin_auth_challenges set encrypted_secret=?,nonce=?,key_version=? "
                        + "where challenge_hash=? and consumed_at is null and expires_at>? and encrypted_secret is null",
                secret.ciphertext(), secret.nonce(), secret.keyVersion(), challengeHash,
                Timestamp.from(Instant.now())) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean incrementChallengeAttempt(String hash) {
        return jdbc.update("update admin_auth_challenges set attempt_count=attempt_count+1 where challenge_hash=? "
                        + "and consumed_at is null and expires_at>? and attempt_count<5",
                hash, Timestamp.from(Instant.now())) == 1;
    }

    public boolean consumeChallenge(String hash) {
        return jdbc.update("update admin_auth_challenges set consumed_at=? where challenge_hash=? and consumed_at is null "
                        + "and expires_at>? and attempt_count<=5",
                Timestamp.from(Instant.now()), hash, Timestamp.from(Instant.now())) == 1;
    }

    public void replaceRecoveryCodes(String subject, List<String> hashes, Instant now) {
        jdbc.update("update admin_recovery_codes set replaced_at=? where subject_id=? and used_at is null and replaced_at is null",
                Timestamp.from(now), subject);
        for (String hash : hashes) {
            jdbc.update("insert into admin_recovery_codes(subject_id,code_hash,created_at) values(?,?,?)",
                    subject, hash, Timestamp.from(now));
        }
    }

    public List<String> activeRecoveryHashes(String subject) {
        return jdbc.query("select code_hash from admin_recovery_codes where subject_id=? and used_at is null "
                + "and replaced_at is null", (rs, row) -> rs.getString(1), subject);
    }

    public boolean consumeRecoveryHash(String subject, String hash) {
        return jdbc.update("update admin_recovery_codes set used_at=? where subject_id=? and code_hash=? "
                        + "and used_at is null and replaced_at is null",
                Timestamp.from(Instant.now()), subject, hash) == 1;
    }

    public int activeRecoveryCount(String subject) {
        Integer count = jdbc.queryForObject("select count(*) from admin_recovery_codes where subject_id=? "
                + "and used_at is null and replaced_at is null", Integer.class, subject);
        return count == null ? 0 : count;
    }

    public void insertSession(String sessionHash, String subject, String scope, AdminAuthPolicy policy,
                              String assurance, Instant passwordAt, Instant totpAt, long credentialVersion,
                              String passwordCredentialHash, Instant issuedAt, Instant expiresAt,
                              Instant idleExpiresAt) {
        jdbc.update("insert into admin_sessions(session_hash,subject_id,scope,environment,auth_mode,assurance,"
                        + "password_authenticated_at,totp_authenticated_at,credential_version,password_credential_hash,"
                        + "issued_at,expires_at,idle_expires_at,last_seen_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                sessionHash, subject, scope, policy.environment(), policy.authMode(), assurance,
                passwordAt == null ? null : Timestamp.from(passwordAt), totpAt == null ? null : Timestamp.from(totpAt),
                credentialVersion, passwordCredentialHash, Timestamp.from(issuedAt), Timestamp.from(expiresAt),
                Timestamp.from(idleExpiresAt), Timestamp.from(issuedAt));
    }

    public Optional<Session> activeSession(String sessionHash, AdminAuthPolicy policy, String passwordCredentialHash,
                                           int sessionIdleMinutes, int recoverySessionIdleMinutes) {
        Instant now = Instant.now();
        List<Session> found = jdbc.query("select subject_id,scope,assurance,password_authenticated_at,totp_authenticated_at,"
                        + "credential_version,expires_at from admin_sessions where session_hash=? and environment=? and auth_mode=? "
                        + "and password_credential_hash=? and revoked_at is null and expires_at>? and idle_expires_at>?",
                (rs, row) -> new Session(sessionHash, rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getLong(6),
                        rs.getTimestamp(7).toInstant()), sessionHash, policy.environment(), policy.authMode(),
                passwordCredentialHash, Timestamp.from(now), Timestamp.from(now));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.getFirst();
        if (policy.totpRequired()) {
            Long currentVersion = jdbc.query("select credential_version from admin_totp_credentials where subject_id=?",
                    rs -> rs.next() ? rs.getLong(1) : null, session.subject());
            if (currentVersion == null || currentVersion != session.credentialVersion()) {
                return Optional.empty();
            }
        }
        int idleMinutes = Math.max(1, RECOVERY_SCOPE.equals(session.scope())
                ? recoverySessionIdleMinutes : sessionIdleMinutes);
        Instant nextIdleExpiry = now.plusSeconds(idleMinutes * 60L);
        if (nextIdleExpiry.isAfter(session.expiresAt())) {
            nextIdleExpiry = session.expiresAt();
        }
        int touched = jdbc.update("update admin_sessions set last_seen_at=?,idle_expires_at=? where session_hash=? "
                        + "and revoked_at is null and expires_at>? and idle_expires_at>?",
                Timestamp.from(now), Timestamp.from(nextIdleExpiry), sessionHash, Timestamp.from(now), Timestamp.from(now));
        return touched == 1 ? Optional.of(session) : Optional.empty();
    }

    public void revokeSession(String hash) {
        jdbc.update("update admin_sessions set revoked_at=? where session_hash=? and revoked_at is null",
                Timestamp.from(Instant.now()), hash);
    }

    public void revokeAll(String subject) {
        jdbc.update("update admin_sessions set revoked_at=? where subject_id=? and revoked_at is null",
                Timestamp.from(Instant.now()), subject);
    }

    public void insertOperationChallenge(String hash, String subject, String sessionHash, String action,
                                         String target, Instant expiresAt) {
        jdbc.update("insert into admin_operation_challenges(challenge_hash,subject_id,session_hash,action_key,target_id,"
                        + "expires_at,attempt_count,created_at) values(?,?,?,?,?,?,?,?)",
                hash, subject, sessionHash, action, target, Timestamp.from(expiresAt), 0, Timestamp.from(Instant.now()));
    }

    public Optional<OperationChallenge> operationChallenge(String hash) {
        return jdbc.query("select subject_id,session_hash,action_key,target_id,expires_at,attempt_count,consumed_at "
                        + "from admin_operation_challenges where challenge_hash=?",
                (rs, row) -> new OperationChallenge(hash, rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getInt(6),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()), hash).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean incrementOperationAttempt(String hash) {
        return jdbc.update("update admin_operation_challenges set attempt_count=attempt_count+1 where challenge_hash=? "
                        + "and consumed_at is null and expires_at>? and attempt_count<5",
                hash, Timestamp.from(Instant.now())) == 1;
    }

    public boolean consumeOperationChallenge(String hash) {
        return jdbc.update("update admin_operation_challenges set consumed_at=? where challenge_hash=? "
                        + "and consumed_at is null and expires_at>? and attempt_count<=5",
                Timestamp.from(Instant.now()), hash, Timestamp.from(Instant.now())) == 1;
    }

    public void insertProof(String hash, OperationChallenge challenge, Instant expiresAt) {
        jdbc.update("insert into admin_operation_proofs(proof_hash,subject_id,session_hash,action_key,target_id,"
                        + "expires_at,created_at) values(?,?,?,?,?,?,?)",
                hash, challenge.subject(), challenge.sessionHash(), challenge.action(), challenge.target(),
                Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }

    public Optional<OperationProof> operationProofForUpdate(String hash) {
        return jdbc.query("select subject_id,session_hash,action_key,target_id,expires_at,consumed_at "
                        + "from admin_operation_proofs where proof_hash=? for update",
                (rs, row) -> new OperationProof(hash, rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getTimestamp(5).toInstant(),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), hash).stream().findFirst();
    }

    public boolean consumeProofHash(String hash) {
        return jdbc.update("update admin_operation_proofs set consumed_at=? where proof_hash=? and consumed_at is null",
                Timestamp.from(Instant.now()), hash) == 1;
    }

    public void cleanupExpiredAuthenticationState(Instant cutoff) {
        Timestamp timestamp = Timestamp.from(cutoff);
        jdbc.update("delete from admin_operation_proofs where expires_at<?", timestamp);
        jdbc.update("delete from admin_operation_challenges where expires_at<?", timestamp);
        jdbc.update("delete from admin_auth_challenges where expires_at<?", timestamp);
        jdbc.update("delete from admin_sessions where expires_at<?", timestamp);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String event, String subject, String sessionHash, String source, String result, String reason) {
        jdbc.update("insert into admin_auth_audit(event_type,subject_id,session_hash,source,result,reason_code,created_at) "
                        + "values(?,?,?,?,?,?,?)",
                event, subject, sessionHash, source, result, reason, Timestamp.from(Instant.now()));
    }

    public record Credential(String encryptedSecret, byte[] nonce, String keyVersion,
                             Long lastAcceptedTimestep, long credentialVersion) {
    }

    public record Challenge(String hash, String subject, String purpose, String sessionHash, Instant passwordAt,
                            String encryptedSecret, byte[] nonce, String keyVersion, Instant expiresAt,
                            int attempts, Instant consumedAt) {
    }

    public record Session(String hash, String subject, String scope, String assurance, Instant passwordAt,
                          Instant totpAt, long credentialVersion, Instant expiresAt) {
    }

    public record OperationChallenge(String hash, String subject, String sessionHash, String action, String target,
                                     Instant expiresAt, int attempts, Instant consumedAt) {
    }

    public record OperationProof(String hash, String subject, String sessionHash, String action, String target,
                                 Instant expiresAt, Instant consumedAt) {
    }
}
