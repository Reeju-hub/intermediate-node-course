document.addEventListener('DOMContentLoaded', () => {
    const registerForm = document.getElementById('registerForm');
    const loginForm = document.getElementById('loginForm');
    const messageDiv = document.getElementById('message');

    if (registerForm) {
        registerForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const username = registerForm.username.value;
            const password = registerForm.password.value;
            messageDiv.innerHTML = ''; // Clear previous messages

            try {
                const response = await fetch('/register', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ username, password }),
                });
                const data = await response.json();
                if (response.ok) {
                    messageDiv.innerHTML = `<p style="color: green;">${data.message}</p>`;
                    registerForm.reset();
                } else {
                    messageDiv.innerHTML = `<p style="color: red;">Error: ${data.message}</p>`;
                }
            } catch (error) {
                messageDiv.innerHTML = `<p style="color: red;">An unexpected error occurred: ${error.toString()}</p>`;
            }
        });
    }

    if (loginForm) {
        loginForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const username = loginForm.username.value;
            const password = loginForm.password.value;
            messageDiv.innerHTML = ''; // Clear previous messages

            try {
                const response = await fetch('/login', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ username, password }),
                });
                const data = await response.json();
                if (response.ok) {
                    messageDiv.innerHTML = `<p style="color: green;">${data.message}. Redirecting to chat...</p>`;
                    loginForm.reset();
                    // Pass user_id to the chat page
                    window.location.href = `/chat?user_id=${data.user_id}`;
                } else {
                    messageDiv.innerHTML = `<p style="color: red;">Error: ${data.message}</p>`;
                }
            } catch (error) {
                messageDiv.innerHTML = `<p style="color: red;">An unexpected error occurred: ${error.toString()}</p>`;
            }
        });
    }
});
