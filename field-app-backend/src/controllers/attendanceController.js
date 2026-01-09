const Attendance = require('../models/Attendance');
const moment = require('moment');

// @desc    Submit attendance
// @route   POST /api/attendance/submit
// @access  Private
exports.submitAttendance = async (req, res) => {
  try {
    const { latitude, longitude, timestamp } = req.body;
    
    // Check if selfie file is uploaded
    if (!req.file) {
      return res.status(400).json({
        success: false,
        message: 'Selfie image is required'
      });
    }

    // Parse timestamp
    const timestampDate = new Date(parseInt(timestamp));
    const date = moment(timestampDate).format('YYYY-MM-DD');
    const checkInTime = moment(timestampDate).format('HH:mm:ss');

    // Create attendance record
    const attendance = await Attendance.create({
      userId: req.user._id,
      employeeId: req.user.employeeId,
      selfiePath: req.file.path,
      latitude: parseFloat(latitude),
      longitude: parseFloat(longitude),
      timestamp: timestampDate,
      date: date,
      checkInTime: checkInTime
    });

    res.status(201).json({
      success: true,
      message: 'Attendance submitted successfully',
      data: attendance
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get today's attendance
// @route   GET /api/attendance/today
// @access  Private
exports.getTodayAttendance = async (req, res) => {
  try {
    const today = moment().format('YYYY-MM-DD');
    
    const attendance = await Attendance.find({
      userId: req.user._id,
      date: today
    }).sort({ timestamp: -1 });

    res.status(200).json({
      success: true,
      count: attendance.length,
      data: attendance
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get attendance by date range
// @route   GET /api/attendance?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
// @access  Private
exports.getAttendance = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    let query = { userId: req.user._id };
    
    if (startDate && endDate) {
      query.date = {
        $gte: startDate,
        $lte: endDate
      };
    }

    const attendance = await Attendance.find(query).sort({ timestamp: -1 });

    res.status(200).json({
      success: true,
      count: attendance.length,
      data: attendance
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get attendance statistics
// @route   GET /api/attendance/stats
// @access  Private
exports.getAttendanceStats = async (req, res) => {
  try {
    const today = moment().format('YYYY-MM-DD');
    const thisMonth = moment().format('YYYY-MM');

    // Count today's check-ins
    const todayCount = await Attendance.countDocuments({
      userId: req.user._id,
      date: today
    });

    // Count this month's check-ins
    const monthCount = await Attendance.countDocuments({
      userId: req.user._id,
      date: { $regex: `^${thisMonth}` }
    });

    // Count total check-ins
    const totalCount = await Attendance.countDocuments({
      userId: req.user._id
    });

    res.status(200).json({
      success: true,
      data: {
        today: todayCount,
        thisMonth: monthCount,
        total: totalCount
      }
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Delete attendance record
// @route   DELETE /api/attendance/:id
// @access  Private
exports.deleteAttendance = async (req, res) => {
  try {
    const attendance = await Attendance.findById(req.params.id);

    if (!attendance) {
      return res.status(404).json({
        success: false,
        message: 'Attendance record not found'
      });
    }

    // Check if attendance belongs to user
    if (attendance.userId.toString() !== req.user._id.toString()) {
      return res.status(403).json({
        success: false,
        message: 'Not authorized to delete this record'
      });
    }

    await attendance.deleteOne();

    res.status(200).json({
      success: true,
      message: 'Attendance record deleted successfully'
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};