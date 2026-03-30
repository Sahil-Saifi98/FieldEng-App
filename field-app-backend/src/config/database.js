const mongoose = require('mongoose');

// Primary Database Connection
const connectPrimaryDB = async () => {
  try {
    const conn = await mongoose.connect(process.env.MONGODB_URI);

    console.log(`✅ Primary MongoDB Connected: ${conn.connection.host}`);
    console.log(`📊 Primary Database: ${conn.connection.name}`);
    return conn.connection;
  } catch (error) {
    console.error(`❌ Primary DB Error: ${error.message}`);
    process.exit(1);
  }
};

// Connect primary DB only — secondary is managed exclusively by dbSync.js
// Having two separate secondary connections was wasting connection pool slots
// and could cause MongoServerSelectionError on free-tier connection limits
const connectDB = async () => {
  await connectPrimaryDB();
};

module.exports = connectDB;