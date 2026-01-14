const Attendance = require('../models/Attendance');
const User = require('../models/User');
const moment = require('moment');

// @desc    Get all users (Admin only)
// @route   GET /api/admin/users
// @access  Private/Admin
exports.getAllUsers = async (req, res) => {
  try {
    const users = await User.find().select('-password');
    
    res.status(200).json({
      success: true,
      count: users.length,
      data: users
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get all attendance records (Admin only)
// @route   GET /api/admin/attendance
// @access  Private/Admin
exports.getAllAttendance = async (req, res) => {
  try {
    const { startDate, endDate, employeeId } = req.query;
    
    let query = {};
    
    if (startDate && endDate) {
      query.date = {
        $gte: startDate,
        $lte: endDate
      };
    }
    
    if (employeeId) {
      query.employeeId = employeeId;
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ timestamp: -1 });

    // Add full image URL and user name
    const attendanceWithUrls = attendance.map(att => {
      const attendanceObj = att.toObject();
      return {
        ...attendanceObj,
        selfieUrl: attendanceObj.selfiePath,
        userName: att.userId ? att.userId.name : 'Unknown User'
      };
    });

    res.status(200).json({
      success: true,
      count: attendanceWithUrls.length,
      data: attendanceWithUrls
    });
  } catch (error) {
    console.error('Get all attendance error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get attendance by user ID (Admin only)
// @route   GET /api/admin/attendance/:userId
// @access  Private/Admin
exports.getUserAttendance = async (req, res) => {
  try {
    const attendance = await Attendance.find({ userId: req.params.userId })
      .populate('userId', 'name employeeId email')
      .sort({ timestamp: -1 });

    const attendanceWithUrls = attendance.map(att => {
      const attendanceObj = att.toObject();
      return {
        ...attendanceObj,
        selfieUrl: attendanceObj.selfiePath,
        userName: att.userId ? att.userId.name : 'Unknown User'
      };
    });

    res.status(200).json({
      success: true,
      count: attendanceWithUrls.length,
      data: attendanceWithUrls
    });
  } catch (error) {
    console.error('Get user attendance error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get attendance statistics for all users (Admin only)
// @route   GET /api/admin/stats
// @access  Private/Admin
exports.getAdminStats = async (req, res) => {
  try {
    const today = moment().format('YYYY-MM-DD');
    const thisMonth = moment().format('YYYY-MM');

    const totalUsers = await User.countDocuments();
    const activeUsers = await User.countDocuments({ isActive: true });
    
    const todayAttendance = await Attendance.countDocuments({ date: today });
    const monthAttendance = await Attendance.countDocuments({ 
      date: { $regex: `^${thisMonth}` } 
    });

    // Get top users by check-ins this month
    const topUsers = await Attendance.aggregate([
      { $match: { date: { $regex: `^${thisMonth}` } } },
      { $group: { _id: '$employeeId', count: { $sum: 1 } } },
      { $sort: { count: -1 } },
      { $limit: 5 }
    ]);

    res.status(200).json({
      success: true,
      data: {
        totalUsers,
        activeUsers,
        todayAttendance,
        monthAttendance,
        topUsers
      }
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Delete user (Admin only)
// @route   DELETE /api/admin/users/:id
// @access  Private/Admin
exports.deleteUser = async (req, res) => {
  try {
    const user = await User.findById(req.params.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    await user.deleteOne();

    res.status(200).json({
      success: true,
      message: 'User deleted successfully'
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Toggle user active status (Admin only)
// @route   PUT /api/admin/users/:id/toggle-active
// @access  Private/Admin
exports.toggleUserActive = async (req, res) => {
  try {
    const user = await User.findById(req.params.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    user.isActive = !user.isActive;
    await user.save();

    res.status(200).json({
      success: true,
      message: `User ${user.isActive ? 'activated' : 'deactivated'} successfully`,
      data: user
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export user data with attendance (Admin only)
// @route   POST /api/admin/export/user/:userId
// @access  Private/Admin
exports.exportUserData = async (req, res) => {
  try {
    const { startDate, endDate } = req.body;
    const userId = req.params.userId;

    // Get user data
    const user = await User.findById(userId).select('-password');
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Get attendance data
    let query = { userId: userId };
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query).sort({ timestamp: -1 });

    // Prepare export data
    const exportData = {
      user: {
        employeeId: user.employeeId,
        name: user.name,
        email: user.email,
        department: user.department,
        designation: user.designation
      },
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      attendance: attendance.map(att => ({
        date: att.date,
        checkInTime: att.checkInTime,
        latitude: att.latitude,
        longitude: att.longitude,
        selfieUrl: att.selfiePath,
        timestamp: att.timestamp
      })),
      exportDate: new Date().toISOString()
    };

    res.status(200).json({
      success: true,
      data: exportData
    });
  } catch (error) {
    console.error('Export user data error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export all data (Admin only)
// @route   POST /api/admin/export/all
// @access  Private/Admin
exports.exportAllData = async (req, res) => {
  try {
    const { startDate, endDate } = req.body;

    // Build query
    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    // Get all attendance with user data
    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ timestamp: -1 });

    // Prepare export data
    const exportData = {
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      attendance: attendance.map(att => ({
        employeeId: att.employeeId,
        employeeName: att.userId ? att.userId.name : 'Unknown',
        department: att.userId ? att.userId.department : '',
        designation: att.userId ? att.userId.designation : '',
        date: att.date,
        checkInTime: att.checkInTime,
        latitude: att.latitude,
        longitude: att.longitude,
        selfieUrl: att.selfiePath,
        timestamp: att.timestamp
      })),
      exportDate: new Date().toISOString()
    };

    res.status(200).json({
      success: true,
      data: exportData
    });
  } catch (error) {
    console.error('Export all data error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export attendance as CSV (Admin only)
// @route   GET /api/admin/export/attendance/csv
// @access  Private/Admin
exports.exportAttendanceCSV = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ timestamp: -1 });

    // Create CSV content
    let csv = 'Employee ID,Employee Name,Department,Designation,Date,Check-in Time,Latitude,Longitude,Selfie URL\n';
    
    attendance.forEach(att => {
      csv += `"${att.employeeId}",`;
      csv += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csv += `"${att.userId ? att.userId.department : ''}",`;
      csv += `"${att.userId ? att.userId.designation : ''}",`;
      csv += `"${att.date}",`;
      csv += `"${att.checkInTime}",`;
      csv += `${att.latitude},`;
      csv += `${att.longitude},`;
      csv += `"${att.selfiePath}"\n`;
    });

    // Set headers for CSV download
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', `attachment; filename=attendance_${Date.now()}.csv`);
    res.status(200).send(csv);
  } catch (error) {
    console.error('Export CSV error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export attendance as JSON (Admin only)
// @route   GET /api/admin/export/attendance/json
// @access  Private/Admin
exports.exportAttendanceJSON = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ timestamp: -1 });

    const exportData = {
      exportDate: new Date().toISOString(),
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      attendance: attendance.map(att => ({
        employeeId: att.employeeId,
        employeeName: att.userId ? att.userId.name : 'Unknown',
        department: att.userId ? att.userId.department : '',
        designation: att.userId ? att.userId.designation : '',
        date: att.date,
        checkInTime: att.checkInTime,
        latitude: att.latitude,
        longitude: att.longitude,
        selfieUrl: att.selfiePath,
        timestamp: att.timestamp
      }))
    };

    // Set headers for JSON download
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Content-Disposition', `attachment; filename=attendance_${Date.now()}.json`);
    res.status(200).json(exportData);
  } catch (error) {
    console.error('Export JSON error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};