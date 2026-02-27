const express = require('express');
const router = express.Router();
const {
  submitTrip,
  getMyTrips,
  getTrip,
  getTripStats,
  getAllTrips,
  updateTripStatus,
  getAdminTripStats
} = require('../controllers/tripController');
const { protect, authorize } = require('../middleware/auth');
const { uploadExpense } = require('../middleware/upload');

// All routes require authentication
router.use(protect);

// ── Employee routes ───────────────────────────────────────────────
// Submit trip with optional receipt images (array upload)
router.post('/submit', uploadExpense.array('receipts', 10), submitTrip);

// Get stats
router.get('/stats', getTripStats);

// Get all my trips
router.get('/', getMyTrips);

// Get single trip
router.get('/:id', getTrip);

// ── Admin routes ──────────────────────────────────────────────────
router.get('/admin/all', authorize('admin'), getAllTrips);
router.get('/admin/stats', authorize('admin'), getAdminTripStats);
router.put('/admin/:id/status', authorize('admin'), updateTripStatus);

module.exports = router;