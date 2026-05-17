alter table site_site
    add column login_route varchar(255) default '/user/login' after primary_domain;

update site_site
set login_route = '/user/login'
where login_route is null or login_route = '';
