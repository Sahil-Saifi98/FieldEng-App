const express = require('express');
const router = express.Router();
const {
  getAllUsers,
  getAllAttendance,
  getUserAttendance,
  getAdminStats,
  deleteUser,
  toggleUserActive,
  exportUserData,
  exportAllData,
  exportAttendanceCSV,
  exportAttendanceJSON
} = require('../controllers/adminController');
const { protect, authorize } = require('../middleware/auth');

// All routes require authentication and admin role
router.use(protect);
router.use(authorize('admin'));

// User Management Routes
router.get('/users', getAllUsers);
router.delete('/users/:id', deleteUser);
router.put('/users/:id/toggle-active', toggleUserActive);

// Attendance Routes
router.get('/attendance', getAllAttendance);
router.get('/attendance/:userId', getUserAttendance);

// Statistics Route
router.get('/stats', getAdminStats);

// Export Routes
router.post('/export/user/:userId', exportUserData);
router.post('/export/all', exportAllData);
router.get('/export/attendance/csv', exportAttendanceCSV);
router.get('/export/attendance/json', exportAttendanceJSON);

module.exports = router;