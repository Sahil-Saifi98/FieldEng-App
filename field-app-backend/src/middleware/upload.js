const multer = require('multer');
const path = require('path');
const { selfieStorage, expenseStorage, taskStorage } = require('../config/cloudinary');

// Cloudinary upload for selfies
const uploadSelfie = multer({
  storage: selfieStorage,
  limits: { fileSize: 10485760 }, // 10MB
  fileFilter: (req, file, cb) => {
    console.log('Uploading selfie to Cloudinary:', {
      fieldname: file.fieldname,
      originalname: file.originalname,
      mimetype: file.mimetype
    });

    if (
      file.mimetype === 'image/jpeg' ||
      file.mimetype === 'image/jpg' ||
      file.mimetype === 'image/png' ||
      file.mimetype === 'image/webp' ||
      file.mimetype === 'application/octet-stream'
    ) {
      cb(null, true);
    } else {
      console.error('Invalid file type:', file.mimetype);
      cb(new Error(`File type ${file.mimetype} not allowed for selfies.`), false);
    }
  }
});

// Cloudinary upload for expenses
const uploadExpense = multer({
  storage: expenseStorage,
  limits: { fileSize: 10485760 }, // 10MB
  fileFilter: (req, file, cb) => {
    if (
      file.mimetype === 'image/jpeg' ||
      file.mimetype === 'image/jpg' ||
      file.mimetype === 'image/png' ||
      file.mimetype === 'application/pdf'
    ) {
      cb(null, true);
    } else {
      cb(new Error(`File type ${file.mimetype} not allowed for expenses.`), false);
    }
  }
});

// Cloudinary upload for tasks
const uploadTask = multer({
  storage: taskStorage,
  limits: { fileSize: 10485760 }, // 10MB
  fileFilter: (req, file, cb) => {
    if (
      file.mimetype === 'image/jpeg' ||
      file.mimetype === 'image/jpg' ||
      file.mimetype === 'image/png' ||
      file.mimetype === 'application/pdf'
    ) {
      cb(null, true);
    } else {
      cb(new Error(`File type ${file.mimetype} not allowed for tasks.`), false);
    }
  }
});

module.exports = {
  uploadSelfie,
  uploadExpense,
  uploadTask
};