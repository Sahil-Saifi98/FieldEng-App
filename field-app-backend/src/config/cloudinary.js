const cloudinary = require('cloudinary').v2;
const { CloudinaryStorage } = require('multer-storage-cloudinary');

cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET,
  timeout: 120000  // 2 min — prevents timeout on slow connections
});

// Selfies — fieldapp/selfies
const selfieStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/selfies',
    allowed_formats: ['jpg', 'jpeg', 'png'],
    transformation: [
      { width: 600, height: 600, crop: 'limit' },
      { quality: 'auto:low' }
    ]
  }
});

// Expense receipts — fieldapp/receipts (separate from selfies)
const expenseStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/receipts',           // ← separate folder
    allowed_formats: ['jpg', 'jpeg', 'png', 'pdf'],
    transformation: [
      { width: 1200, crop: 'limit' },      // keep readable for admin review
      { quality: 'auto:good' }             // better quality than selfies
    ]
  }
});

// Task attachments — fieldapp/tasks
const taskStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/tasks',
    allowed_formats: ['jpg', 'jpeg', 'png', 'pdf'],
    transformation: [{ quality: 'auto:low' }]
  }
});

module.exports = { cloudinary, selfieStorage, expenseStorage, taskStorage };