const multer = require('multer');
const path = require('path');
const fs = require('fs');

// Ensure upload directories exist
const ensureDirectoryExists = (dir) => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
};

// Configure storage
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    let uploadPath = 'uploads/';
    
    if (file.fieldname === 'selfie') {
      uploadPath += 'selfies/';
    } else if (file.fieldname === 'receipt') {
      uploadPath += 'expenses/';
    } else if (file.fieldname === 'attachment') {
      uploadPath += 'tasks/';
    }
    
    // Ensure directory exists
    ensureDirectoryExists(uploadPath);
    
    cb(null, uploadPath);
  },
  filename: function (req, file, cb) {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

// File filter - More flexible for mobile uploads
const fileFilter = (req, file, cb) => {
  console.log('Uploaded file:', {
    fieldname: file.fieldname,
    originalname: file.originalname,
    mimetype: file.mimetype,
    size: file.size
  });

  // Accept images and PDFs
  if (
    file.mimetype === 'image/jpeg' ||
    file.mimetype === 'image/jpg' ||
    file.mimetype === 'image/png' ||
    file.mimetype === 'image/webp' ||
    file.mimetype === 'application/pdf' ||
    file.mimetype === 'application/octet-stream' // Sometimes mobile uploads use this
  ) {
    cb(null, true);
  } else {
    console.error('Invalid file type:', file.mimetype);
    cb(new Error(`File type ${file.mimetype} not allowed. Only images and PDFs are accepted.`), false);
  }
};

// Create multer upload instance
const upload = multer({
  storage: storage,
  limits: { 
    fileSize: 10485760 // 10MB
  },
  fileFilter: fileFilter
});

module.exports = upload;