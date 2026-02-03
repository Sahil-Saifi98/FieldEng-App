const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

// Load environment variables from parent directory
dotenv.config({ path: path.join(__dirname, '../.env') });

const Attendance = require('../src/models/Attendance');
const { getAddressFromCoordinates } = require('../src/utils/geocoder');

async function populateAddresses() {
  try {
    console.log('🚀 Starting address population script...\n');
    console.log('🔄 Connecting to MongoDB...');
    
    // Changed from MONGO_URI to MONGODB_URI
    if (!process.env.MONGODB_URI) {
      throw new Error('MONGODB_URI not found in environment variables. Please check your .env file.');
    }
    
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('✅ Connected to MongoDB\n');
    
    // First, let's see how many total records exist
    const totalCount = await Attendance.countDocuments();
    console.log(`📊 Total attendance records in database: ${totalCount}`);
    
    // Check records with each condition separately
    const emptyAddress = await Attendance.countDocuments({ address: '' });
    const noAddress = await Attendance.countDocuments({ address: { $exists: false } });
    const nullAddress = await Attendance.countDocuments({ address: null });
    
    console.log(`   - Empty address (''): ${emptyAddress}`);
    console.log(`   - No address field: ${noAddress}`);
    console.log(`   - Null address: ${nullAddress}`);
    
    // Find all records without an address
    const records = await Attendance.find({ 
      $or: [
        { address: '' }, 
        { address: { $exists: false } },
        { address: null }
      ] 
    });
    
    console.log(`\n📊 Found ${records.length} records without addresses\n`);
    
    if (records.length === 0) {
      console.log('ℹ️  No records found matching criteria.');
      console.log('\nLet\'s check a sample record:');
      const sample = await Attendance.findOne();
      if (sample) {
        console.log('Sample record:');
        console.log(JSON.stringify(sample.toObject(), null, 2));
      } else {
        console.log('No records exist in the database.');
      }
      await mongoose.connection.close();
      process.exit(0);
    }
    
    let successCount = 0;
    let failCount = 0;
    
    for (let i = 0; i < records.length; i++) {
      const record = records[i];
      console.log(`[${i + 1}/${records.length}] Processing: ${record.employeeId}`);
      console.log(`   Location: ${record.latitude}, ${record.longitude}`);
      
      try {
        const address = await getAddressFromCoordinates(record.latitude, record.longitude);
        record.address = address;
        await record.save();
        
        console.log(`   ✅ ${address}\n`);
        successCount++;
        
        // Delay to avoid rate limiting (1 second between requests)
        if (i < records.length - 1) {
          await new Promise(resolve => setTimeout(resolve, 1000));
        }
      } catch (error) {
        console.log(`   ❌ Error: ${error.message}\n`);
        failCount++;
      }
    }
    
    console.log('='.repeat(50));
    console.log('📊 Summary:');
    console.log(`   ✅ Success: ${successCount}`);
    console.log(`   ❌ Failed: ${failCount}`);
    console.log(`   📝 Total: ${records.length}`);
    console.log('='.repeat(50));
    
    await mongoose.connection.close();
    console.log('\n✅ Database connection closed');
    process.exit(0);
  } catch (error) {
    console.error('\n❌ Fatal Error:', error.message);
    console.error(error);
    if (mongoose.connection.readyState === 1) {
      await mongoose.connection.close();
    }
    process.exit(1);
  }
}

// Run the script
populateAddresses();