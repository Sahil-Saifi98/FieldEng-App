const mongoose = require('mongoose');

let secondaryConnection = null;

// Initialize secondary connection
const initSecondaryConnection = async () => {
  try {
    secondaryConnection = mongoose.createConnection(
      process.env.MONGODB_URI_SECONDARY
    );

    secondaryConnection.on('connected', () => {
      console.log('🔄 Secondary DB ready for sync');
    });

    return secondaryConnection;
  } catch (error) {
    console.error('Secondary DB sync init failed:', error.message);
    return null;
  }
};

// Get model for secondary database
const getSecondaryModel = (modelName, schema) => {
  if (!secondaryConnection) {
    return null;
  }
  return secondaryConnection.model(modelName, schema);
};

// Sync data to secondary database
const syncToSecondary = async (modelName, schema, data) => {
  try {
    if (!secondaryConnection) {
      await initSecondaryConnection();
    }

    if (!secondaryConnection) {
      console.log('⚠️  Secondary DB not available, skipping sync');
      return false;
    }

    const SecondaryModel = getSecondaryModel(modelName, schema);
    
    if (!SecondaryModel) {
      return false;
    }

    // Create or update in secondary database
    if (data._id) {
      await SecondaryModel.findOneAndUpdate(
        { _id: data._id },
        data,
        { upsert: true, new: true }
      );
    } else {
      await SecondaryModel.create(data);
    }

    console.log(`✅ Synced ${modelName} to secondary DB`);
    return true;
  } catch (error) {
    console.error(`❌ Sync to secondary failed: ${error.message}`);
    return false;
  }
};

module.exports = {
  initSecondaryConnection,
  syncToSecondary,
  getSecondaryModel
};