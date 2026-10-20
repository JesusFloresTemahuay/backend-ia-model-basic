-- Restricciones FK con ON DELETE CASCADE: replican en H2 el esquema real de PostgreSQL
-- (conversations_user_id_fkey y messages_conversation_id_fkey).
-- Se ejecuta después de que Hibernate cree las tablas (spring.jpa.defer-datasource-initialization=true).

ALTER TABLE conversations
    ADD CONSTRAINT conversations_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE messages
    ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE;

-- El DDL de Hibernate crea los String como varchar(255), pero en el esquema real
-- content y message son texto libre (respuestas del modelo, markdowns, instrucciones
-- de sistema...): se amplían a TEXT para que el esquema de test refleje el real.
ALTER TABLE messages ALTER COLUMN content SET DATA TYPE TEXT;
ALTER TABLE messages ALTER COLUMN message SET DATA TYPE TEXT;
