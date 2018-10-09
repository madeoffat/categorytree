CREATE table categories(genre_id int primary key, genre_path int, genre_name varchar(30));
CREATE table items(id int primary key auto_increment, genre_id int, rank int, genre_name varchar(30), item_name varchar(500), index idx_item(genre_id, rank));
CREATE table child_relations(id int primary key auto_increment, genre_id int, child_genre_id int, index idx_ralation(genre_id, child_genre_id));
);
