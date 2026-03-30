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

// Secondary Database Connection (HR Database)
const connectSecondaryDB = async () => {
  try {
    const secondaryConn = mongoose.createConnection(
      process.env.MONGODB_URI_SECONDARY,
      {
        useNewUrlParser: true,
        useUnifiedTopology: true,
      }
    );

    secondaryConn.on('connected', () => {
      console.log(`✅ Secondary MongoDB Connected: ${secondaryConn.host}`);
      console.log(`📊 Secondary Database: ${secondaryConn.name}`);
    });

    secondaryConn.on('error', (err) => {
  console.error(`❌ Secondary DB Error: ${err?.message || err || 'unknown'}`);
});

    return secondaryConn;
  } catch (error) {
    console.error(`❌ Secondary DB Connection Error: ${error.message}`);
    return null;
  }
};

// Connect both databases
const connectDB = async () => {
  const primaryDB = await connectPrimaryDB();
  const secondaryDB = await connectSecondaryDB();
  
  return { primaryDB, secondaryDB };
};

module.exports = connectDB;