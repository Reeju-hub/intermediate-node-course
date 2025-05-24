// currentUserId and currentUsername are passed from chat.html <script> block

const userListUl = document.getElementById('user-list');
const messagesDiv = document.getElementById('messages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const currentRecipientIdInput = document.getElementById('current-recipient-id');
const currentChatPartnerNameSpan = document.getElementById('current-chat-partner-name');

let socket; 
let activeUsersCache = []; // To find usernames by ID if not in current list

document.addEventListener('DOMContentLoaded', () => {
    if (typeof currentUserId === 'undefined' || currentUserId === null) {
        messagesDiv.innerHTML = "<p style='color:red'>Error: User ID not available. Please log in again.</p>";
        console.error("currentUserId is not defined. Check chat.html is passing it.");
        // Disable chat functionality if user ID is missing
        messageInput.disabled = true;
        sendButton.disabled = true;
        return;
    }

    console.log(`Chat loaded for User ID: ${currentUserId}, Username: ${currentUsername}`);

    // Connect to Socket.IO server
    socket = io.connect(window.location.origin); // Assumes Flask and SocketIO are on the same host/port

    socket.on('connect', () => {
        console.log('Socket.IO connected successfully.');
        socket.emit('identify_user', { user_id: currentUserId });
    });

    socket.on('user_identified', (data) => {
        console.log('Server acknowledged user identification:', data);
    });
    
    socket.on('disconnect', () => {
        console.warn('Socket.IO disconnected.');
        appendMessage('System', 'Disconnected from chat server. Attempting to reconnect...', 'system-message');
        // Optionally, try to reconnect or notify user more formally.
    });

    socket.on('error_message', (data) => {
        console.error('Received error from server:', data.error);
        appendMessage('System', `Error: ${data.error}`, 'system-message error-message');
    });

    socket.on('delivery_status', (data) => {
        console.log('Message delivery status:', data);
        // Example: appendMessage('System', `Message to ${findUsernameById(data.recipient_id)}: ${data.status}`, 'system-message');
    });

    loadUserList(); // Fetch and display user list

    sendButton.addEventListener('click', sendMessage);
    messageInput.addEventListener('keypress', (event) => {
        if (event.key === 'Enter' && !event.shiftKey) { // Send on Enter, unless Shift+Enter for newline
            event.preventDefault(); // Prevent default Enter behavior (e.g., form submission)
            sendMessage();
        }
    });

    // Listen for new private messages from the server
    socket.on('new_private_message', (data) => {
        console.log('New private message received:', data);
        const selectedRecipientId = parseInt(currentRecipientIdInput.value);

        // Message is for me from the person I'm currently chatting with
        if (data.sender_id === selectedRecipientId && data.recipient_id === currentUserId) {
            appendMessage(findUsernameById(data.sender_id), data.content, data.timestamp, false);
        } 
        // Message is for me, but from someone else (not the currently selected chat partner)
        else if (data.recipient_id === currentUserId && data.sender_id !== selectedRecipientId) {
            appendMessage(findUsernameById(data.sender_id), data.content, data.timestamp, false, true); // True for notification style
            
            const userLi = document.querySelector(`#user-list li[data-userid="${data.sender_id}"]`);
            if (userLi && !userLi.classList.contains('selected-user')) {
                userLi.classList.add('unread-message');
            }
        }
        // If it's a message I sent, it's already handled client-side (optimistic update).
    });
});

async function loadUserList() {
    if (!userListUl) {
        console.error('User list UL element not found in chat.html.');
        return;
    }

    try {
        const response = await fetch('/users');
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: 'Unknown error fetching users.' }));
            throw new Error(errorData.message || `HTTP error ${response.status}`);
        }
        const users = await response.json();
        activeUsersCache = users; // Cache for username lookups
        
        userListUl.innerHTML = ''; // Clear current list

        if (users.length <= 1) { // Only current user or no other users
            userListUl.innerHTML = '<li>No other users currently online.</li>';
        } else {
            users.forEach(user => {
                if (user.id === currentUserId) return; // Skip self in the list

                const li = document.createElement('li');
                li.textContent = user.username;
                li.dataset.userid = user.id;
                li.dataset.username = user.username;
                li.addEventListener('click', () => selectUserForChat(user.id, user.username));
                userListUl.appendChild(li);
            });
        }
    } catch (error) {
        console.error('Failed to load user list:', error);
        userListUl.innerHTML = `<li>Error loading users: ${error.message}</li>`;
    }
}

function findUsernameById(userId) {
    if (userId === currentUserId) return currentUsername; // It's me
    
    // Try from the list items first (might be most up-to-date if list reloads)
    const userLi = document.querySelector(`#user-list li[data-userid="${userId}"]`);
    if (userLi && userLi.dataset.username) {
        return userLi.dataset.username;
    }
    // Fallback to cache
    const cachedUser = activeUsersCache.find(u => u.id === parseInt(userId));
    if (cachedUser) return cachedUser.username;
    
    console.warn(`Username for ID ${userId} not found in list or cache. Consider refreshing user list.`);
    return `User ${userId}`; // Fallback display
}

function selectUserForChat(userId, username) {
    currentRecipientIdInput.value = userId;
    currentChatPartnerNameSpan.textContent = username;
    messagesDiv.innerHTML = `<p class='system-message'>You are now chatting with ${username}.</p>`; 
    
    messageInput.disabled = false;
    sendButton.disabled = false;
    messageInput.focus();

    document.querySelectorAll('#user-list li').forEach(li => {
        li.classList.remove('selected-user');
        if (li.dataset.userid == userId) {
            li.classList.add('selected-user');
            li.classList.remove('unread-message'); 
        }
    });
    // Future enhancement: Fetch message history for this chat
    // fetchMessageHistory(currentUserId, userId); 
}

function sendMessage() {
    const messageText = messageInput.value.trim();
    const recipientId = parseInt(currentRecipientIdInput.value);

    if (!messageText) return; // Don't send empty messages
    if (!recipientId) {
        appendMessage('System', 'Please select a user to chat with.', 'system-message error-message');
        return;
    }
    if (!socket || !socket.connected) {
        appendMessage('System', 'Not connected to chat server. Please wait or refresh.', 'system-message error-message');
        return;
    }

    socket.emit('private_message', {
        recipient_id: recipientId,
        message: messageText
    });

    appendMessage(currentUsername, messageText, new Date().toISOString(), true); // Optimistic update
    messageInput.value = '';
}

function appendMessage(userName, text, timestamp, isSender, isNotification = false) {
    const messageElement = document.createElement('div');
    messageElement.classList.add('message');
    
    const senderSpan = document.createElement('span');
    senderSpan.classList.add('sender-name');
    
    const textSpan = document.createElement('span');
    textSpan.classList.add('message-text');
    textSpan.textContent = text; // Text content for security

    const timeSpan = document.createElement('span');
    timeSpan.classList.add('message-time');
    timeSpan.textContent = ` (${new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})`;

    if (isSender) {
        messageElement.classList.add('sent');
        senderSpan.textContent = `You: `; 
    } else {
        messageElement.classList.add('received');
        senderSpan.textContent = `${userName}: `;
        if (isNotification) {
            messageElement.classList.add('notification'); // Special style for new message from non-selected user
            // Could also make it a link to switch to that chat.
            textSpan.innerHTML += ` <em style="font-size:0.8em">(New message from ${userName}. Click on user to view.)</em>`;
        }
    }
    
    if (userName === 'System') { // System messages
        messageElement.classList.add('system-message');
        senderSpan.textContent = ''; // No sender name for system messages
        timeSpan.textContent = ''; // No timestamp for system messages usually
    }


    messageElement.appendChild(senderSpan);
    messageElement.appendChild(textSpan);
    if (userName !== 'System') { // Don't add time for system messages
       messageElement.appendChild(timeSpan);
    }
    
    messagesDiv.appendChild(messageElement);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

// Initial setup when the window (and currentUserId/currentUsername) are available
// Note: DOMContentLoaded is used above, which is generally preferred.
// window.onload can be a fallback or for things that must wait for all resources (like images).
window.onload = () => {
    if (typeof currentUserId === 'undefined') {
         console.warn("window.onload: currentUserId not set. Chat setup might be incomplete if DOMContentLoaded failed.");
    } else {
        console.log("window.onload: Chat page fully loaded for user:", currentUsername);
    }
};
