const Attendance = require('../models/Attendance');
const User = require('../models/User');
const moment = require('moment');
const archiver = require('archiver');
const PDFDocument = require('pdfkit');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const { promisify } = require('util');
const stream = require('stream');
const pipeline = promisify(stream.pipeline);

// Helper function to download image from URL
async function downloadImage(url, filepath) {
  try {
    const response = await axios({
      method: 'GET',
      url: url,
      responseType: 'stream'
    });
    
    const writer = fs.createWriteStream(filepath);
    await pipeline(response.data, writer);
    return true;
  } catch (error) {
    console.error(`Failed to download image from ${url}:`, error.message);
    return false;
  }
}

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

// @desc    Export user data with attendance and selfies as ZIP
// @route   POST /api/admin/export/user/:userId
// @access  Private/Admin
exports.exportUserData = async (req, res) => {
  const tempDir = path.join(__dirname, '../temp');
  const userTempDir = path.join(tempDir, `user_${req.params.userId}_${Date.now()}`);

  try {
    const { startDate, endDate } = req.body;
    const userId = req.params.userId;

    // Set timeout to 5 minutes for large exports
    req.setTimeout(300000);

    // Create temp directory
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }
    fs.mkdirSync(userTempDir, { recursive: true });

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

    const attendance = await Attendance.find(query)
      .sort({ timestamp: -1 })
      .limit(500); // Limit to prevent memory issues

    console.log(`Exporting ${attendance.length} attendance records for user ${user.employeeId}`);

    // Create CSV content
    let csvContent = 'Date,Check-in Time,Latitude,Longitude,Selfie URL\n';
    
    // Download selfies directory
    const selfiesDir = path.join(userTempDir, 'selfies');
    fs.mkdirSync(selfiesDir, { recursive: true });

    // Download selfies with error handling and limits
    const downloadPromises = [];
    const maxConcurrent = 5; // Download max 5 images at a time
    
    for (let i = 0; i < attendance.length; i++) {
      const att = attendance[i];
      const filename = `selfie_${att.date}_${att.checkInTime.replace(/:/g, '-')}.jpg`;
      const filepath = path.join(selfiesDir, filename);

      // Add to CSV with URL reference
      csvContent += `"${att.date}","${att.checkInTime}",${att.latitude},${att.longitude},"${att.selfiePath}"\n`;

      // Download selfie if URL exists
      if (att.selfiePath) {
        const downloadTask = downloadImage(att.selfiePath, filepath)
          .catch(err => {
            console.error(`Failed to download ${filename}:`, err.message);
            return false;
          });
        
        downloadPromises.push(downloadTask);

        // Process in batches to avoid overwhelming the server
        if (downloadPromises.length >= maxConcurrent || i === attendance.length - 1) {
          await Promise.all(downloadPromises);
          downloadPromises.length = 0; // Clear array
        }
      }
    }

    // Save CSV file
    const csvPath = path.join(userTempDir, `${user.employeeId}_attendance.csv`);
    fs.writeFileSync(csvPath, csvContent);

    // Save user info JSON
    const userInfoPath = path.join(userTempDir, 'user_info.json');
    fs.writeFileSync(userInfoPath, JSON.stringify({
      employeeId: user.employeeId,
      name: user.name,
      email: user.email,
      department: user.department,
      designation: user.designation,
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      exportDate: new Date().toISOString()
    }, null, 2));

    // Create ZIP archive
    const archive = archiver('zip', { 
      zlib: { level: 6 } // Reduce compression level for faster processing
    });
    
    const zipFilename = `${user.employeeId}_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');

    // Handle archive errors
    archive.on('error', (err) => {
      console.error('Archive error:', err);
      throw err;
    });

    archive.on('warning', (err) => {
      if (err.code === 'ENOENT') {
        console.warn('Archive warning:', err);
      } else {
        throw err;
      }
    });

    // Pipe archive to response
    archive.pipe(res);

    // Add all files to ZIP
    archive.directory(userTempDir, false);

    // Finalize archive
    await archive.finalize();

    console.log(`âœ… Export completed: ${zipFilename}`);

    // Cleanup temp directory after a delay
    setTimeout(() => {
      try {
        if (fs.existsSync(userTempDir)) {
          fs.rmSync(userTempDir, { recursive: true, force: true });
          console.log(`Cleaned up temp directory: ${userTempDir}`);
        }
      } catch (cleanupErr) {
        console.error('Cleanup error:', cleanupErr);
      }
    }, 5000); // Wait 5 seconds before cleanup

  } catch (error) {
    console.error('âŒ Export user data error:', error);
    
    // Cleanup on error
    try {
      if (fs.existsSync(userTempDir)) {
        fs.rmSync(userTempDir, { recursive: true, force: true });
      }
    } catch (cleanupErr) {
      console.error('Cleanup error:', cleanupErr);
    }

    // Send error response if headers not sent
    if (!res.headersSent) {
      res.status(500).json({
        success: false,
        message: error.message || 'Export failed'
      });
    }
  }
};

// @desc    Export all data as ZIP
// @route   POST /api/admin/export/all
// @access  Private/Admin
exports.exportAllData = async (req, res) => {
  const tempDir = path.join(__dirname, '../temp');
  const exportTempDir = path.join(tempDir, `all_export_${Date.now()}`);

  try {
    const { startDate, endDate } = req.body;

    // Set longer timeout for large exports
    req.setTimeout(300000);

    // Create temp directory
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }
    fs.mkdirSync(exportTempDir, { recursive: true });

    // Build query
    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    // Get all attendance with user data (limit to prevent memory issues)
    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ timestamp: -1 })
      .limit(1000); // Limit records

    console.log(`Exporting ${attendance.length} total attendance records`);

    // Create CSV content
    let csvContent = 'Employee ID,Employee Name,Department,Designation,Date,Check-in Time,Latitude,Longitude,Selfie URL\n';
    
    attendance.forEach(att => {
      csvContent += `"${att.employeeId}",`;
      csvContent += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csvContent += `"${att.userId ? att.userId.department : ''}",`;
      csvContent += `"${att.userId ? att.userId.designation : ''}",`;
      csvContent += `"${att.date}",`;
      csvContent += `"${att.checkInTime}",`;
      csvContent += `${att.latitude},`;
      csvContent += `${att.longitude},`;
      csvContent += `"${att.selfiePath}"\n`;
    });

    // Save CSV
    const csvPath = path.join(exportTempDir, 'all_attendance.csv');
    fs.writeFileSync(csvPath, csvContent);

    // Save summary JSON
    const summaryPath = path.join(exportTempDir, 'summary.json');
    fs.writeFileSync(summaryPath, JSON.stringify({
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      exportDate: new Date().toISOString(),
      note: 'Selfie images are referenced by URL in the CSV file. For offline access, use individual user export.'
    }, null, 2));

    // Create ZIP
    const archive = archiver('zip', { zlib: { level: 6 } });
    const zipFilename = `all_data_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');

    archive.on('error', (err) => {
      console.error('Archive error:', err);
      throw err;
    });

    archive.pipe(res);
    archive.directory(exportTempDir, false);

    await archive.finalize();

    console.log(`âœ… Export all completed: ${zipFilename}`);

    // Cleanup
    setTimeout(() => {
      try {
        if (fs.existsSync(exportTempDir)) {
          fs.rmSync(exportTempDir, { recursive: true, force: true });
        }
      } catch (cleanupErr) {
        console.error('Cleanup error:', cleanupErr);
      }
    }, 5000);

  } catch (error) {
    console.error('âŒ Export all data error:', error);
    
    try {
      if (fs.existsSync(exportTempDir)) {
        fs.rmSync(exportTempDir, { recursive: true, force: true });
      }
    } catch (cleanupErr) {
      console.error('Cleanup error:', cleanupErr);
    }

    if (!res.headersSent) {
      res.status(500).json({
        success: false,
        message: error.message || 'Export failed'
      });
    }
  }
};

// @desc    Export attendance as CSV
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

// @desc    Export attendance as PDF
// @route   GET /api/admin/export/attendance/pdf
// @access  Private/Admin
exports.exportAttendancePDF = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ timestamp: -1 });

    // Create PDF
    const doc = new PDFDocument({ margin: 50, size: 'A4' });
    const filename = `attendance_${Date.now()}.pdf`;

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);

    doc.pipe(res);

    // Title
    doc.fontSize(20).text('Attendance Report', { align: 'center' });
    doc.moveDown();
    
    // Date range
    doc.fontSize(12).text(`Date Range: ${startDate || 'All'} to ${endDate || 'All'}`, { align: 'center' });
    doc.fontSize(12).text(`Total Records: ${attendance.length}`, { align: 'center' });
    doc.fontSize(10).text(`Generated: ${new Date().toLocaleString()}`, { align: 'center' });
    doc.moveDown(2);

    // Table headers
    const tableTop = doc.y;
    const rowHeight = 25;
    let currentY = tableTop;

    // Draw header
    doc.fontSize(8).font('Helvetica-Bold');
    doc.text('Emp ID', 50, currentY, { width: 60 });
    doc.text('Name', 110, currentY, { width: 100 });
    doc.text('Date', 210, currentY, { width: 70 });
    doc.text('Time', 280, currentY, { width: 60 });
    doc.text('Location', 340, currentY, { width: 120 });
    
    currentY += rowHeight;
    doc.moveTo(50, currentY - 5).lineTo(550, currentY - 5).stroke();

    // Data rows
    doc.font('Helvetica');
    attendance.forEach((att, index) => {
      if (currentY > 700) {
        doc.addPage();
        currentY = 50;
        
        // Redraw header on new page
        doc.font('Helvetica-Bold').fontSize(8);
        doc.text('Emp ID', 50, currentY, { width: 60 });
        doc.text('Name', 110, currentY, { width: 100 });
        doc.text('Date', 210, currentY, { width: 70 });
        doc.text('Time', 280, currentY, { width: 60 });
        doc.text('Location', 340, currentY, { width: 120 });
        currentY += rowHeight;
        doc.moveTo(50, currentY - 5).lineTo(550, currentY - 5).stroke();
        doc.font('Helvetica');
      }

      doc.fontSize(7);
      doc.text(att.employeeId || '', 50, currentY, { width: 60 });
      doc.text(att.userId ? att.userId.name : 'Unknown', 110, currentY, { width: 100 });
      doc.text(att.date, 210, currentY, { width: 70 });
      doc.text(att.checkInTime, 280, currentY, { width: 60 });
      doc.text(`${att.latitude.toFixed(4)}, ${att.longitude.toFixed(4)}`, 340, currentY, { width: 120 });
      
      currentY += rowHeight;
      
      // Light separator line
      if (index < attendance.length - 1) {
        doc.moveTo(50, currentY - 2).lineTo(550, currentY - 2).stroke('#CCCCCC');
      }
    });

    doc.end();

  } catch (error) {
    console.error('Export PDF error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export attendance as JSON
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