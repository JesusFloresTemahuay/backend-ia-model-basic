-- Restricciones FK con ON DELETE CASCADE: replican en H2 el esquema real de PostgreSQL
-- (conversations_user_id_fkey y messages_conversation_id_fkey).
-- Se ejecuta después de que Hibernate cree las tablas (spring.jpa.defer-datasource-initialization=true).

ALTER TABLE conversations
    ADD CONSTRAINT conversations_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE messages
    ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE;
