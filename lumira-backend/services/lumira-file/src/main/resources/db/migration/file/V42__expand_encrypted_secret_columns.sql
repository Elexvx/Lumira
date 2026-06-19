alter table file_storage_space
    modify column access_key_secret varchar(2048) default null;
