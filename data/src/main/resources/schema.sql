-- Drop everything
--drop all objects;

-- Event table
create table if not exists event (
    id identity primary key,
    device_name varchar(50) not null,
    event_type varchar(50) not null,
    event_data varchar(255) null,
    event_time timestamp not null
);

-- Device name index
create index if not exists event_device_name on event(device_name, event_type, event_time);

-- Detection frame
create table if not exists frame (
    id identity primary key,
    event_id bigint not null,
    frame_time timestamp not null,
    constraint fk_frame_event foreign key (event_id) references event(id) on delete cascade
);

-- Deepstack detection
create table if not exists detection (
    id identity primary key,
    frame_id bigint not null,
    label_ varchar(50) not null,
    confidence float not null,
    y_min int not null,
    x_min int not null,
    y_max int not null,
    x_max int not null,
    constraint fk_detection_frame foreign key (frame_id) references frame(id) on delete cascade
);
