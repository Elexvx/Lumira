alter table file_storage_space
    modify column access_key_secret varchar(2048) default null;

alter table sys_verification_binding
    modify column secret_key varchar(512) default null;

alter table sys_verification_challenge
    modify column setup_secret varchar(512) default null;
