import unittest
from flask_testing import TestCase
from chat_app.backend.app import app, db, User, Message, socketio # Adjusted import
from werkzeug.security import generate_password_hash
from datetime import datetime

class BaseTestCase(TestCase):
    """A base test case for the chat application."""

    def create_app(self):
        # Use a separate in-memory SQLite database for testing
        app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///:memory:'
        app.config['TESTING'] = True
        app.config['WTF_CSRF_ENABLED'] = False # Disable CSRF for testing forms if any
        app.config['SECRET_KEY'] = 'test_secret_key' # Consistent secret key
        # Ensure SocketIO is initialized with the test app instance
        # If socketio was initialized globally in app.py, this might need adjustment
        # or ensure app.py uses an app factory pattern.
        # For now, assuming app.socketio works or can be re-initialized.
        # If your app.py initializes socketio = SocketIO(app) directly,
        # then self.app already has it. If it's SocketIO(), then re-init.
        # socketio.init_app(app) # This line might be needed if SocketIO is not app-bound
        return app

    def setUp(self):
        with self.app.app_context():
            db.create_all()
            # Add a couple of initial users for tests that need existing users
            user1 = User(username='testuser1', password_hash=generate_password_hash('password123'))
            user2 = User(username='testuser2', password_hash=generate_password_hash('password456'))
            db.session.add_all([user1, user2])
            db.session.commit()

    def tearDown(self):
        with self.app.app_context():
            db.session.remove()
            db.drop_all()

class TestAuthEndpoints(BaseTestCase):
    def test_register_success(self):
        response = self.client.post('/register', json={
            'username': 'newuser',
            'password': 'newpassword'
        })
        self.assertEqual(response.status_code, 201)
        self.assertIn('User created successfully', response.json['message'])
        with self.app.app_context():
            user = User.query.filter_by(username='newuser').first()
            self.assertIsNotNone(user)

    def test_register_existing_username(self):
        response = self.client.post('/register', json={
            'username': 'testuser1', # Existing user from setUp
            'password': 'newpassword'
        })
        self.assertEqual(response.status_code, 409)
        self.assertIn('Username already exists', response.json['message'])

    def test_register_missing_fields(self):
        response = self.client.post('/register', json={'username': 'someuser'}) # Missing password
        self.assertEqual(response.status_code, 400)
        self.assertIn('Username and password are required', response.json['message'])

    def test_login_success(self):
        response = self.client.post('/login', json={
            'username': 'testuser1',
            'password': 'password123'
        })
        self.assertEqual(response.status_code, 200)
        self.assertIn('Login successful', response.json['message'])
        self.assertIn('user_id', response.json)
        self.assertEqual(response.json['user_id'], User.query.filter_by(username='testuser1').first().id)


    def test_login_incorrect_password(self):
        response = self.client.post('/login', json={
            'username': 'testuser1',
            'password': 'wrongpassword'
        })
        self.assertEqual(response.status_code, 401)
        self.assertIn('Invalid username or password', response.json['message'])

    def test_login_non_existent_username(self):
        response = self.client.post('/login', json={
            'username': 'nouser',
            'password': 'somepassword'
        })
        self.assertEqual(response.status_code, 401) # Or 404 depending on implementation detail
        self.assertIn('Invalid username or password', response.json['message'])
    
    def test_login_missing_fields(self):
        response = self.client.post('/login', json={'username': 'testuser1'}) # Missing password
        self.assertEqual(response.status_code, 400)
        self.assertIn('Username and password are required', response.json['message'])


class TestUserEndpoint(BaseTestCase):
    def test_get_users(self):
        # Users testuser1 and testuser2 are created in setUp
        response = self.client.get('/users')
        self.assertEqual(response.status_code, 200)
        users_data = response.json
        self.assertIsInstance(users_data, list)
        self.assertEqual(len(users_data), 2) # testuser1, testuser2
        
        usernames = [user['username'] for user in users_data]
        self.assertIn('testuser1', usernames)
        self.assertIn('testuser2', usernames)
        
        # Check structure
        for user_data in users_data:
            self.assertIn('id', user_data)
            self.assertIn('username', user_data)

class TestDatabaseModels(BaseTestCase):
    def test_create_user_model(self):
        with self.app.app_context():
            username = "modeluser"
            password = "modelpassword"
            hashed_password = generate_password_hash(password)
            
            user = User(username=username, password_hash=hashed_password)
            db.session.add(user)
            db.session.commit()
            
            retrieved_user = User.query.filter_by(username=username).first()
            self.assertIsNotNone(retrieved_user)
            self.assertEqual(retrieved_user.username, username)
            self.assertTrue(retrieved_user.password_hash.startswith('pbkdf2:sha256')) # Werkzeug hash format

    def test_create_message_model(self):
        with self.app.app_context():
            sender = User.query.filter_by(username='testuser1').first()
            receiver = User.query.filter_by(username='testuser2').first()
            
            self.assertIsNotNone(sender, "Test setup error: sender 'testuser1' not found")
            self.assertIsNotNone(receiver, "Test setup error: receiver 'testuser2' not found")

            content = "Hello, testuser2!"
            message = Message(sender_id=sender.id, receiver_id=receiver.id, content=content)
            db.session.add(message)
            db.session.commit()

            retrieved_message = Message.query.filter_by(content=content).first()
            self.assertIsNotNone(retrieved_message)
            self.assertEqual(retrieved_message.sender_id, sender.id)
            self.assertEqual(retrieved_message.receiver_id, receiver.id)
            self.assertEqual(retrieved_message.content, content)
            self.assertIsInstance(retrieved_message.timestamp, datetime)

            # Test relationships (optional but good)
            self.assertIn(retrieved_message, sender.messages_sent)
            self.assertIn(retrieved_message, receiver.messages_received)

if __name__ == '__main__':
    unittest.main()
