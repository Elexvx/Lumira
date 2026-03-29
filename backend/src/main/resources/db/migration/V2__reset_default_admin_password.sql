UPDATE sys_user
SET password_hash = '$2a$10$ko3RP4YpfVgyQC5pZjq5t.d1TKrqmBGoehczMjqn1k.pLeAAnTI9G'
WHERE id = 1001
   OR username = 'admin';
