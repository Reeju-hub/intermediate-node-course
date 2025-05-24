from flask import Flask, request, jsonify, render_template
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from flask_socketio import SocketIO, emit
import os
from datetime import datetime

app = Flask(__name__)
app.config['SECRET_KEY'] = 'your_secret_key_here' # Important for SocketIO

# Configure database
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///chat.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)
socketio = SocketIO(app)

# User SIDs mapping
user_sids = {}

# Define User model
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    
    messages_sent = db.relationship('Message', foreign_keys='Message.sender_id', backref='sender', lazy=True)
    messages_received = db.relationship('Message', foreign_keys='Message.receiver_id', backref='receiver', lazy=True)

    def __repr__(self):
        return f'<User {self.username}>'

# Define Message model
class Message(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    sender_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    receiver_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    content = db.Column(db.Text, nullable=False)
    timestamp = db.Column(db.DateTime, default=datetime.utcnow, nullable=False)

    def __repr__(self):
        return f'<Message from {self.sender_id} to {self.receiver_id} at {self.timestamp}>'

# Database initialization function
def init_db():
    with app.app_context():
        db.create_all()

# Register route
@app.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')

    if not username or not password:
        return jsonify({'message': 'Username and password are required'}), 400

    if User.query.filter_by(username=username).first():
        return jsonify({'message': 'Username already exists'}), 409

    hashed_password = generate_password_hash(password)
    new_user = User(username=username, password_hash=hashed_password)
    db.session.add(new_user)
    db.session.commit()

    return jsonify({'message': 'User created successfully'}), 201

# Login route
@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')

    if not username or not password:
        return jsonify({'message': 'Username and password are required'}), 400

    user = User.query.filter_by(username=username).first()

    if not user or not check_password_hash(user.password_hash, password):
        return jsonify({'message': 'Invalid username or password'}), 401

    return jsonify({'message': 'Login successful', 'user_id': user.id}), 200

# Route to get all users
@app.route('/users', methods=['GET'])
def get_users():
    # TODO: Secure this endpoint later (e.g., require authentication)
    users = User.query.all()
    user_list = [{'id': user.id, 'username': user.username} for user in users]
    return jsonify(user_list), 200

# Routes to serve HTML pages
@app.route('/show_register')
def show_register():
    return render_template('register.html')

@app.route('/show_login')
def show_login():
    return render_template('login.html')

@app.route('/chat')
def chat():
    user_id = request.args.get('user_id')
    if not user_id:
        # For a real app, redirect to login or show a proper error page
        # from flask import redirect, url_for
        # return redirect(url_for('show_login', error='User ID missing'))
        return "Error: User ID is required. Please login again.", 400
    
    # You might want to validate if user_id is a valid user here
    user = User.query.get(user_id)
    if not user:
        return "Error: Invalid User ID.", 404
        
    return render_template('chat.html', current_user_id=user_id, current_username=user.username)

@app.route('/')
def index():
    # For now, let's redirect to the login page as a simple landing page
    return render_template('login.html')

# SocketIO event handlers
@socketio.on('connect')
def handle_connect():
    print(f"Client connected: {request.sid}")
    # Client should send 'identify_user' with their user_id

@socketio.on('identify_user')
def handle_identify_user(data):
    user_id = data.get('user_id')
    if user_id:
        user_sids[user_id] = request.sid
        print(f"User {user_id} connected with SID {request.sid}")
        # Optionally, confirm identification to the client
        emit('user_identified', {'user_id': user_id, 'sid': request.sid})
    else:
        print(f"Identify failed: No user_id provided by {request.sid}")


@socketio.on('disconnect')
def handle_disconnect():
    disconnected_user_id = None
    for user_id, sid in user_sids.items():
        if sid == request.sid:
            disconnected_user_id = user_id
            break
    if disconnected_user_id:
        del user_sids[disconnected_user_id]
        print(f"User {disconnected_user_id} (SID: {request.sid}) disconnected.")
    else:
        print(f"Client disconnected: {request.sid} (User ID not identified).")

@socketio.on('private_message')
def handle_private_message(data):
    recipient_id = data.get('recipient_id')
    message_content = data.get('message')
    
    sender_id = None
    for uid, sid_val in user_sids.items():
        if sid_val == request.sid:
            sender_id = uid
            break

    if not sender_id:
        print(f"Message from unidentified SID {request.sid}. Ignoring.")
        # Optionally, emit an error back to the sender
        emit('error_message', {'error': 'User not identified. Cannot send message.'}, room=request.sid)
        return

    if not recipient_id or not message_content:
        emit('error_message', {'error': 'Recipient ID and message content are required.'}, room=request.sid)
        return

    # Ensure recipient_id is an integer if it's coming as a string
    try:
        recipient_id = int(recipient_id)
    except ValueError:
        emit('error_message', {'error': 'Invalid recipient ID format.'}, room=request.sid)
        return


    # Create and store the message
    new_message = Message(sender_id=sender_id, receiver_id=recipient_id, content=message_content)
    db.session.add(new_message)
    db.session.commit()
    print(f"Message from {sender_id} to {recipient_id} saved: {message_content}")

    recipient_sid = user_sids.get(recipient_id)
    if recipient_sid:
        message_data = {
            'sender_id': sender_id,
            'recipient_id': recipient_id,
            'content': new_message.content,
            'timestamp': new_message.timestamp.isoformat() # Send timestamp as ISO string
        }
        socketio.emit('new_private_message', message_data, room=recipient_sid)
        print(f"Message sent to SID {recipient_sid}")
    else:
        print(f"Recipient {recipient_id} is not connected.")
        # Optionally, inform sender that recipient is offline
        emit('delivery_status', {'recipient_id': recipient_id, 'status': 'offline'}, room=request.sid)


if __name__ == '__main__':
    db_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'chat.db')
    # Check if the database file exists. If not, or if it's empty, initialize.
    # For schema changes, the database might need to be recreated or migrated.
    # For simplicity, we re-initialize if it's not found.
    # A more robust solution would use Flask-Migrate for schema changes.
    with app.app_context():
        db.create_all() # This will create tables if they don't exist.
                        # It won't update existing tables if the schema changes.
                        # For schema changes, you might need to delete chat.db manually
                        # during development or use a migration tool like Flask-Migrate.
        print("Database tables ensured/created.")
    
    print("Starting SocketIO server...")
    socketio.run(app, debug=True, host='0.0.0.0', port=5000) # Use 0.0.0.0 to be accessible externally
