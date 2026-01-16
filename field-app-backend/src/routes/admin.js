const express = require('express');
const router = express.Router();
const Attendance = require('../models/Attendance');
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
  exportAttendancePDF,
  exportAttendanceJSON
} = require('../controllers/adminController');
const { protect, authorize } = require('../middleware/auth');

// All routes require authentication and admin role
router.use(protect);
router.use(authorize('admin'));

// DEBUG ROUTE - Test date queries
router.get('/test-dates', async (req, res) => {
  try {
    const { startDate, endDate, userId } = req.query;
    
    console.log('Testing dates:', { startDate, endDate, userId });
    
    // Test 1: All records
    const allCount = await Attendance.countDocuments();
    
    // Test 2: Date range query
    let dateQuery = {};
    if (startDate && endDate) {
      dateQuery.date = { $gte: startDate, $lte: endDate };
    }
    const dateCount = await Attendance.countDocuments(dateQuery);
    
    // Test 3: With user filter
    let userQuery = { ...dateQuery };
    if (userId) {
      userQuery.userId = userId;
    }
    const userCount = await Attendance.countDocuments(userQuery);
    
    // Get sample records
    const sampleAll = await Attendance.find()
      .limit(5)
      .select('date employeeId checkInTime userId')
      .sort({ date: -1 });
    
    const sampleDate = await Attendance.find(dateQuery)
      .limit(5)
      .select('date employeeId checkInTime userId')
      .sort({ date: -1 });
    
    const sampleUser = await Attendance.find(userQuery)
      .limit(5)
      .select('date employeeId checkInTime userId')
      .sort({ date: -1 });
    
    // Get distinct dates
    const distinctDates = await Attendance.distinct('date');
    
    res.json({
      success: true,
      test: {
        parameters: { startDate, endDate, userId },
        counts: {
          allRecords: allCount,
          dateRangeRecords: dateCount,
          userRecords: userCount
        },
        samples: {
          allRecords: sampleAll,
          dateRangeRecords: sampleDate,
          userRecords: sampleUser
        },
        distinctDates: distinctDates.sort().reverse().slice(0, 10),
        queries: {
          dateQuery,
          userQuery
        }
      }
    });
  } catch (error) {
    console.error('Test dates error:', error);
    res.status(500).json({ 
      success: false, 
      error: error.message,
      stack: error.stack 
    });
  }
});

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
router.get('/export/attendance/pdf', exportAttendancePDF);
router.get('/export/attendance/json', exportAttendanceJSON);

module.exports = router;