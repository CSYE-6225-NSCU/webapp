CREATE TABLE IF NOT EXISTS sent_emails (
   email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    expiry TIMESTAMP NOT NULL,
    status VARCHAR(50),
    PRIMARY KEY (token)
    );
