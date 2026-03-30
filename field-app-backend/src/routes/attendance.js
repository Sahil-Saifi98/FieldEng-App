const express = require('express');
const router = express.Router();
const {
  submitAttendance,
  getTodayAttendance,
  getAttendance,
  getAttendanceStats,
  deleteAttendance
} = require('../controllers/attendanceController');
const { protect } = require('../middleware/auth');
const { uploadSelfie } = require('../middleware/upload');

// All routes are protected (require authentication)
router.use(protect);

// Submit attendance with selfie upload to Cloudinary
// Wrapping multer explicitly so upload errors return a clean JSON response instead of
// falling through to the global error handler (which would log "Error: undefined")
router.post('/submit', (req, res, next) => {
  uploadSelfie.single('selfie')(req, res, (err) => {
    if (err) {
      // err can be a MulterError, a plain Error, or a Cloudinary plain object
      const message = err?.message || err?.error?.message || 'Failed to upload selfie';
      console.error('⚠️ Upload middleware error:', message, err?.error || '');
      return res.status(500).json({
        success: false,
        message: 'Failed to upload selfie. Please check your connection and try again.'
      });
    }
    next();
  });
}, submitAttendance);

// Get today's attendance
router.get('/today', getTodayAttendance);

// Get attendance with optional date range
router.get('/', getAttendance);

// Get attendance statistics
router.get('/stats', getAttendanceStats);

// Delete attendance record
router.delete('/:id', deleteAttendance);

module.exports = router;