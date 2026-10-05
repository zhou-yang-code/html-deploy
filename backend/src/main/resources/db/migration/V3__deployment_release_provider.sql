alter table deployment
    add column provider varchar(32);

update deployment
   set provider = case
       when release_path like 'http%' then 'netlify'
       else 'local'
   end
 where provider is null;

alter table deployment
    alter column provider set not null;
