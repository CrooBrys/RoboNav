const express = require('express');
const mysql = require('mysql2');
const dotenv = require('dotenv');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const nodemailer = require('nodemailer');
const crypto = require('crypto');

// Configure nodemailer transport
const transporter = nodemailer.createTransport({
    service: 'gmail',  // You can use any email service
    auth: {
        user: process.env.EMAIL_USER,  // Your email
        pass: process.env.EMAIL_PASS,  // Your email password
    }
});

dotenv.config();

const app = express();
const PORT = process.env.PORT || 8080;
const SECRET_KEY = process.env.JWT_SECRET || 'your_jwt_secret'; // Replace with a secure secret

app.use(express.json()); // For parsing JSON body

app.use(cors({
    origin: '*', // Allow all origins
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));

// MySQL Connection
const db = mysql.createConnection({
    host: process.env.DB_HOST || 'localhost',
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME || 'robot_info',
    port: process.env.DB_PORT || 3306,
    multipleStatements: true // Allow running multiple SQL commands
});

// Connect to MySQL
db.connect(err => {
    if (err) {
        console.error('Database connection failed:', err);
        return;
    }
    console.log('Connected to MySQL database');

    // Run the SQL dump file
    initializeDatabase();
});

// Function to execute SQL dump file
function initializeDatabase() {
    const sqlDumpPath = path.join(__dirname, '/SQL_Files/robot_info_RoboNav_dump'); // Path to your SQL file
    const sqlDump = fs.readFileSync(sqlDumpPath, 'utf8');

    db.query(sqlDump, (err, results) => {
        if (err) {
            console.error('Error executing SQL dump:', err);
        } else {
            console.log('Database initialized successfully');
        }
    });
}

// Middleware to verify JWT token
const authenticateToken = (req, res, next) => {
    const authHeader = req.header('Authorization');
    if (!authHeader) return res.status(401).json({ message: 'Unauthorized' });

    const token = authHeader.split(' ')[1];
    jwt.verify(token, SECRET_KEY, (err, user) => {
        if (err) return res.status(403).json({ message: 'Invalid or expired token' });

        req.user = user;
        next();
    });
};

// User Registration (Storing Hashed Password)
app.post('/api/open/users/register', async (req, res) => {
    const { username, password, email } = req.body;

    if (!username || !password || !email) {
        return res.status(400).json({ message: 'Username, email, and password are required' });
    }

    try {
        // Check if email already exists
        db.query('SELECT * FROM users WHERE email = ?', [email], async (err, results) => {
            if (err) return res.status(500).json({ message: 'Database error', error: err });

            if (results.length > 0) {
                return res.status(400).json({ message: 'Email already in use' });
            }

            // Hash password
            const hashedPassword = await bcrypt.hash(password, 10);

            // Insert user into database with confirmed = 0 (not confirmed)
            db.query('INSERT INTO users (username, hashed_password, email, confirmed) VALUES (?, ?, ?, 0)',
                [username, hashedPassword, email],
                (err, result) => {
                    if (err) {
                        return res.status(500).json({ message: 'Error inserting user', error: err });
                    }

                    // Send confirmation email
                    const token = crypto.randomBytes(32).toString('hex');  // Create a random token
                    const confirmationLink = `http://localhost:8080/api/open/users/confirm-email?token=${token}`;

                    // Save token in database (no expiration for simplicity)
                    db.query('INSERT INTO email_confirmations (user_id, token) VALUES (?, ?)', [result.insertId, token]);

                    // Send the confirmation email
                    const mailOptions = {
                        from: process.env.EMAIL_USER,
                        to: email,
                        subject: 'Confirm your email',
                        text: `Click the link to confirm your email: ${confirmationLink}`
                    };

                    transporter.sendMail(mailOptions, (error, info) => {
                        if (error) {
                            return res.status(500).json({ message: 'Error sending email', error });
                        }
                        res.status(201).json({ message: 'User registered successfully. Please check your email to confirm your account.' });
                    });
                }
            );
        });
    } catch (error) {
        res.status(500).json({ message: 'Error processing request', error });
    }
});

// Email Confirmation API
app.get('/api/open/users/confirm-email', (req, res) => {
    const { token } = req.query;

    // Check if token exists
    if (!token) {
        return res.status(400).json({ message: 'No token provided' });
    }

    // Check if the token is valid
    db.query('SELECT * FROM email_confirmations WHERE token = ?', [token], (err, results) => {
        if (err) return res.status(500).json({ message: 'Database error', error: err });

        if (results.length === 0) {
            return res.status(400).json({ message: 'Invalid token' });
        }

        // Get the user ID from the confirmation
        const userId = results[0].user_id;

        // Update user confirmation status
        db.query('UPDATE users SET confirmed = 1 WHERE id = ?', [userId], (err, result) => {
            if (err) return res.status(500).json({ message: 'Error updating user', error: err });

            // Delete the token from the database (one-time use)
            db.query('DELETE FROM email_confirmations WHERE token = ?', [token]);

            res.status(200).json({ message: 'Email confirmed successfully. You can now log in.' });
        });
    });
});

// User Login (JWT Token Generation)
app.post('/api/open/users/login', (req, res) => {
    const { username, password } = req.body;

    db.query('SELECT * FROM users WHERE username = ?', [username], async (err, results) => {
        if (err) return res.status(500).json({ message: 'Database error', error: err });

        if (results.length === 0) return res.status(401).json({ message: 'Invalid credentials' });

        const user = results[0];

        // Check if account is disabled
        if (user.confirmed === 2) {
            return res.status(403).json({ message: 'Your account has been disabled by an administrator' });
        }

        // Check if email is confirmed
        if (user.confirmed === 0) {
            return res.status(401).json({ message: 'Please confirm your email before logging in' });
        }

        const passwordMatch = await bcrypt.compare(password, user.hashed_password);

        if (!passwordMatch) return res.status(401).json({ message: 'Invalid credentials' });

        // Generate JWT Token
        const token = jwt.sign({ userId: user.id, username: user.username }, SECRET_KEY, { expiresIn: '1h' });

        res.json({ message: 'Login successful', token });
    });
});

// Get robot tasks
app.get('/api/robot/tasks', authenticateToken, (req, res) => {
    db.query('SELECT * FROM task', (err, results) => {
        if (err) {
            return res.status(500).json({ message: 'Database error' });
        }
        if (results.length === 0) {
            return res.status(404).json({ message: 'No tasks found' });
        }

        const tasks = results.map(task => ({
            id: task.task_id,
            name: task.name,
            robot: task.robot_id,
            progress: 0,
            createdBy: 'n/a',
            dateCreated: task.start
        }));

        res.json(tasks);
    });
});

// Get info for robot
app.get('/api/robot/robots', authenticateToken, (req, res) => {
    const robotQuery = 'SELECT * FROM robot';
    const taskQuery = 'SELECT task_id FROM task WHERE robot_id = ?';
    const locationQuery = `
        SELECT rl.*, l.name 
        FROM robot_location rl
        LEFT JOIN location l 
        ON rl.x = l.x AND rl.y = l.y AND rl.robot_id = l.robot_id
        WHERE rl.robot_id = ? 
        ORDER BY rl.r_loc_id DESC LIMIT 1`;

    db.query(robotQuery, (err, robotResults) => {
        if (err) {
            return res.status(500).json({ message: 'Database error' });
        }
        if (robotResults.length === 0) {
            return res.status(404).json({ message: 'No robots found' });
        }

        const robotsData = [];
        const robotQueries = robotResults.map((robot) => {
            return new Promise((resolve, reject) => {
                db.query(taskQuery, [robot.robot_id], (err, taskResults) => {
                    if (err) return reject({ message: 'Database error' });

                    db.query(locationQuery, [robot.robot_id], (err, locationResults) => {
                        if (err) return reject({ message: 'Database error' });

                        const tasks = taskResults.length > 0 ? taskResults.map(task => task.task_id) : [];
                        const location = locationResults[0] || {};

                        const robotData = {
                            id: robot.robot_id,
                            name: `Robot #${robot.robot_id}`,
                            ping: `${robot.ping || 'N/A'}ms`,
                            battery: robot.battery,
                            location_name: location.name || 'Unknown',
                            location_coordinates: `${location.x || 'N/A'},${location.y || 'N/A'}`,
                            tasks: tasks
                        };

                        robotsData.push(robotData);
                        resolve();
                    });
                });
            });
        });

        Promise.all(robotQueries)
            .then(() => {
                res.json(robotsData);
            })
            .catch((error) => {
                res.status(500).json(error);
            });
    });
});

// Test Route
app.get('/', (req, res) => {
    res.send('Simple Express MySQL API with JWT Authentication is running...');
});

// Start HTTPS Server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
});
