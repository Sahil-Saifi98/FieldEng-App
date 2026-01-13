const cloudinary = require('cloudinary').v2;
const { CloudinaryStorage } = require('multer-storage-cloudinary');

// Configure Cloudinary
cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET
});

// Create storage for selfies
const selfieStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/selfies',
    allowed_formats: ['jpg', 'jpeg', 'png'],
    transformation: [{ width: 800, height: 800, crop: 'limit' }] // Optimize image size
  }
});

// Create storage for expenses
const expenseStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/expenses',
    allowed_formats: ['jpg', 'jpeg', 'png', 'pdf']
  }
});

// Create storage for tasks
const taskStorage = new CloudinaryStorage({
  cloudinary: cloudinary,
  params: {
    folder: 'fieldapp/tasks',
    allowed_formats: ['jpg', 'jpeg', 'png', 'pdf']
  }
});

module.exports = {
  cloudinary,
  selfieStorage,
  expenseStorage,
  taskStorage
};