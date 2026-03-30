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

// Get stats — MUST be before /:id or Express matches /stats as an id
router.get('/stats', getTripStats);

// ── Admin routes — MUST be before /:id ───────────────────────────
// If these were after router.get('/:id'), Express would treat "admin" as an id
// and Trip.findOne({ _id: "admin" }) would throw a Mongoose CastError → 500
router.get('/admin/all', authorize('admin'), getAllTrips);
router.get('/admin/stats', authorize('admin'), getAdminTripStats);
router.put('/admin/:id/status', authorize('admin'), updateTripStatus);

// Get all my trips
router.get('/', getMyTrips);

// Get single trip — keep last, it matches anything
router.get('/:id', getTrip);

module.exports = router;