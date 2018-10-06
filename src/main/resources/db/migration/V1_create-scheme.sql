CREATE table categories(genre_id int primary key, genre_path int, genre_name varchar(30));
CREATE table items(genre_id int primary key, rank int, genre_name varchar(30), item_name varchar(500));

