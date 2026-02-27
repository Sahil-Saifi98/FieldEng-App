const express = require('express');
const dotenv = require('dotenv');
const cors = require('cors');
const path = require('path');
const connectDB = require('./config/database');
const { initSecondaryConnection } = require('./config/dbSync');

// Load environment variables
dotenv.config();

// Connect to MongoDB (Primary and Secondary)
connectDB();

// Initialize secondary DB connection for syncing
initSecondaryConnection();

// Initialize express app
const app = express();

// CRITICAL: Increase timeouts globally for large file exports
app.use((req, res, next) => {
  req.setTimeout(600000); // 10 minutes
  res.setTimeout(600000); // 10 minutes
  next();
});

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' })); // Increase JSON payload limit
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Serve static files (uploaded images)
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

// Routes
app.use('/api/auth', require('./routes/auth'));
app.use('/api/attendance', require('./routes/attendance'));
app.use('/api/trips', require('./routes/trips')); 
app.use('/api/admin', require('./routes/admin'));

// Root route
app.get('/', (req, res) => {
  res.json({
    success: true,
    message: 'Field App API is running',
    version: '1.0.0',
    endpoints: {
      auth: '/api/auth',
      attendance: '/api/attendance',
      trips: '/api/trips',
      admin: '/api/admin'
    }
  });
});

// Handle 404
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: 'Route not found'
  });
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Error:', err.stack);
  res.status(500).json({
    success: false,
    message: err.message || 'Server Error'
  });
});

// Start server
const PORT = process.env.PORT || 5000;
const server = app.listen(PORT, () => {
  console.log(`🚀 Server running on port ${PORT}`);
  console.log(`📡 Environment: ${process.env.NODE_ENV || 'development'}`);
});

// Set server timeouts
server.timeout = 600000; // 10 minutes
server.keepAliveTimeout = 610000; // Slightly more than timeout
server.headersTimeout = 620000; // Slightly more than keepAliveTimeout

module.exports = app;