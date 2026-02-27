const cloudinary = require('cloudinary').v2;
const { CloudinaryStorage } = require('multer-storage-cloudinary');

cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET,
  timeout: 120000 // ← 2 minutes instead of default 60s
});

// Selfie storage — compress more aggressively for slow connections
const selfieStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/selfies',
    allowed_formats: ['jpg', 'jpeg', 'png'],
    transformation: [
      { width: 600, height: 600, crop: 'limit' }, // ← reduced from 800
      { quality: 'auto:low' }                      // ← auto compress
    ]
  }
});

const expenseStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/expenses',
    allowed_formats: ['jpg', 'jpeg', 'png', 'pdf'],
    transformation: [{ quality: 'auto:low' }]
  }
});

const taskStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/tasks',
    allowed_formats: ['jpg', 'jpeg', 'png', 'pdf'],
    transformation: [{ quality: 'auto:low' }]
  }
});

module.exports = { cloudinary, selfieStorage, expenseStorage, taskStorage };