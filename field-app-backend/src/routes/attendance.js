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
const upload = require('../middleware/upload');

// All routes are protected (require authentication)
router.use(protect);

// Submit attendance with selfie upload
router.post('/submit', upload.single('selfie'), submitAttendance);

// Get today's attendance
router.get('/today', getTodayAttendance);

// Get attendance with optional date range
router.get('/', getAttendance);

// Get attendance statistics
router.get('/stats', getAttendanceStats);

// Delete attendance record
router.delete('/:id', deleteAttendance);

module.exports = router;