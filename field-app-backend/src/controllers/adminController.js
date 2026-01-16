const Attendance = require('../models/Attendance');
const User = require('../models/User');
const moment = require('moment-timezone');
const archiver = require('archiver');
const PDFDocument = require('pdfkit');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const { promisify } = require('util');
const stream = require('stream');
const pipeline = promisify(stream.pipeline);

// Helper function to convert UTC to IST
function convertToIST(utcDate) {
  return moment(utcDate).tz('Asia/Kolkata');
}

// Helper function to download image from URL
async function downloadImage(url, filepath) {
  try {
    const response = await axios({
      method: 'GET',
      url: url,
      responseType: 'stream',
      timeout: 30000
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
    const today = moment().tz('Asia/Kolkata').format('YYYY-MM-DD');
    const thisMonth = moment().tz('Asia/Kolkata').format('YYYY-MM');

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

    console.log('Export request:', { userId, startDate, endDate });

    req.setTimeout(600000);
    res.setTimeout(600000);

    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }
    fs.mkdirSync(userTempDir, { recursive: true });

    const user = await User.findById(userId).select('-password');
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    let query = { userId: userId };
    if (startDate && endDate) {
      query.date = {
        $gte: startDate,
        $lte: endDate
      };
    }

    console.log('Query:', JSON.stringify(query));

    const attendance = await Attendance.find(query)
      .sort({ date: -1, timestamp: -1 })
      .limit(1000);

    console.log(`Found ${attendance.length} attendance records for export`);

    if (attendance.length === 0) {
      fs.rmSync(userTempDir, { recursive: true, force: true });
      return res.status(404).json({
        success: false,
        message: 'No attendance records found for the selected date range'
      });
    }

    // Create CSV with IST times
    let csvContent = 'Date,Check-in Time (IST),Latitude,Longitude,Selfie Filename\n';
    
    const selfiesDir = path.join(userTempDir, 'selfies');
    fs.mkdirSync(selfiesDir, { recursive: true });

    // Download images sequentially to avoid issues
    for (let i = 0; i < attendance.length; i++) {
      const att = attendance[i];
      
      // Convert UTC timestamp to IST
      const istTime = convertToIST(att.timestamp).format('HH:mm:ss');
      const istDate = convertToIST(att.timestamp).format('YYYY-MM-DD');
      
      const filename = `selfie_${istDate}_${istTime.replace(/:/g, '-')}.jpg`;
      const filepath = path.join(selfiesDir, filename);

      csvContent += `"${istDate}","${istTime}",${att.latitude},${att.longitude},"selfies/${filename}"\n`;

      // Download selfie image if available
      if (att.selfiePath) {
        console.log(`Downloading selfie ${i + 1}/${attendance.length}: ${att.selfiePath}`);
        await downloadImage(att.selfiePath, filepath);
        // Small delay to avoid overwhelming the server
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }

    const csvPath = path.join(userTempDir, `${user.employeeId}_attendance.csv`);
    fs.writeFileSync(csvPath, csvContent);

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
      exportDate: convertToIST(new Date()).format('YYYY-MM-DD HH:mm:ss')
    }, null, 2));

    // Create ZIP with proper settings for Android compatibility
    const archive = archiver('zip', { 
      zlib: { level: 6 },
      store: true // Use STORE method for better Android compatibility
    });
    
    const zipFilename = `${user.employeeId}_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');

    archive.on('error', (err) => {
      console.error('Archive error:', err);
      if (!res.headersSent) {
        res.status(500).json({ success: false, message: err.message });
      }
      setTimeout(() => {
        if (fs.existsSync(userTempDir)) {
          fs.rmSync(userTempDir, { recursive: true, force: true });
        }
      }, 1000);
    });

    archive.on('warning', (err) => {
      if (err.code !== 'ENOENT') {
        console.warn('Archive warning:', err);
      }
    });

    archive.pipe(res);
    
    // Add files to archive
    archive.file(csvPath, { name: `${user.employeeId}_attendance.csv` });
    archive.file(userInfoPath, { name: 'user_info.json' });
    archive.directory(selfiesDir, 'selfies');

    await archive.finalize();

    console.log(`✅ Export completed: ${zipFilename}, size: ${archive.pointer()} bytes`);

    res.on('finish', () => {
      setTimeout(() => {
        try {
          if (fs.existsSync(userTempDir)) {
            fs.rmSync(userTempDir, { recursive: true, force: true });
            console.log(`Cleaned up: ${userTempDir}`);
          }
        } catch (cleanupErr) {
          console.error('Cleanup error:', cleanupErr);
        }
      }, 2000);
    });

  } catch (error) {
    console.error('❌ Export user data error:', error);
    
    try {
      if (fs.existsSync(userTempDir)) {
        fs.rmSync(userTempDir, { recursive: true, force: true });
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

// @desc    Export all data as ZIP
// @route   POST /api/admin/export/all
// @access  Private/Admin
exports.exportAllData = async (req, res) => {
  const tempDir = path.join(__dirname, '../temp');
  const exportTempDir = path.join(tempDir, `all_export_${Date.now()}`);

  try {
    const { startDate, endDate } = req.body;

    console.log('Export all request:', { startDate, endDate });

    req.setTimeout(600000);
    res.setTimeout(600000);

    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }
    fs.mkdirSync(exportTempDir, { recursive: true });

    let query = {};
    if (startDate && endDate) {
      query.date = {
        $gte: startDate,
        $lte: endDate
      };
    }

    console.log('Query:', JSON.stringify(query));

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(2000);

    console.log(`Found ${attendance.length} total records for export`);

    if (attendance.length === 0) {
      fs.rmSync(exportTempDir, { recursive: true, force: true });
      return res.status(404).json({
        success: false,
        message: 'No attendance records found for the selected date range'
      });
    }

    // Create CSV with IST times
    let csvContent = 'Employee ID,Employee Name,Department,Designation,Date (IST),Check-in Time (IST),Latitude,Longitude,Selfie Filename\n';
    
    const selfiesDir = path.join(exportTempDir, 'selfies');
    fs.mkdirSync(selfiesDir, { recursive: true });

    // Download selfies and create CSV
    for (let i = 0; i < attendance.length; i++) {
      const att = attendance[i];
      
      // Convert to IST
      const istTime = convertToIST(att.timestamp).format('HH:mm:ss');
      const istDate = convertToIST(att.timestamp).format('YYYY-MM-DD');
      
      const filename = `${att.employeeId}_${istDate}_${istTime.replace(/:/g, '-')}.jpg`;
      const filepath = path.join(selfiesDir, filename);

      csvContent += `"${att.employeeId}",`;
      csvContent += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csvContent += `"${att.userId ? att.userId.department : ''}",`;
      csvContent += `"${att.userId ? att.userId.designation : ''}",`;
      csvContent += `"${istDate}",`;
      csvContent += `"${istTime}",`;
      csvContent += `${att.latitude},`;
      csvContent += `${att.longitude},`;
      csvContent += `"selfies/${filename}"\n`;

      // Download selfie if available
      if (att.selfiePath) {
        console.log(`Downloading selfie ${i + 1}/${attendance.length}`);
        await downloadImage(att.selfiePath, filepath);
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }

    const csvPath = path.join(exportTempDir, 'all_attendance.csv');
    fs.writeFileSync(csvPath, csvContent);

    const summaryPath = path.join(exportTempDir, 'summary.json');
    fs.writeFileSync(summaryPath, JSON.stringify({
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      exportDate: convertToIST(new Date()).format('YYYY-MM-DD HH:mm:ss'),
      timezone: 'Asia/Kolkata (IST)'
    }, null, 2));

    // Create ZIP with Android compatibility
    const archive = archiver('zip', { 
      zlib: { level: 6 },
      store: true
    });
    
    const zipFilename = `all_data_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');

    archive.on('error', (err) => {
      console.error('Archive error:', err);
      if (!res.headersSent) {
        res.status(500).json({ success: false, message: err.message });
      }
    });

    archive.pipe(res);
    
    archive.file(csvPath, { name: 'all_attendance.csv' });
    archive.file(summaryPath, { name: 'summary.json' });
    archive.directory(selfiesDir, 'selfies');

    await archive.finalize();

    console.log(`✅ Export all completed: ${zipFilename}`);

    res.on('finish', () => {
      setTimeout(() => {
        try {
          if (fs.existsSync(exportTempDir)) {
            fs.rmSync(exportTempDir, { recursive: true, force: true });
          }
        } catch (cleanupErr) {
          console.error('Cleanup error:', cleanupErr);
        }
      }, 2000);
    });

  } catch (error) {
    console.error('❌ Export all data error:', error);
    
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

    console.log('CSV Export:', { startDate, endDate });

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(5000);

    console.log(`Exporting ${attendance.length} records as CSV`);

    let csv = 'Employee ID,Employee Name,Department,Designation,Date (IST),Check-in Time (IST),Latitude,Longitude\n';
    
    attendance.forEach(att => {
      const istTime = convertToIST(att.timestamp).format('HH:mm:ss');
      const istDate = convertToIST(att.timestamp).format('YYYY-MM-DD');
      
      csv += `"${att.employeeId}",`;
      csv += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csv += `"${att.userId ? att.userId.department : ''}",`;
      csv += `"${att.userId ? att.userId.designation : ''}",`;
      csv += `"${istDate}",`;
      csv += `"${istTime}",`;
      csv += `${att.latitude},`;
      csv += `${att.longitude}\n`;
    });

    const filename = `attendance_${startDate || 'all'}_to_${endDate || 'all'}_${Date.now()}.csv`;
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).send('\ufeff' + csv); // Add BOM for Excel compatibility

    console.log(`✅ CSV exported: ${filename}`);
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

    console.log('PDF Export:', { startDate, endDate });

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(1000);

    console.log(`Exporting ${attendance.length} records as PDF`);

    const doc = new PDFDocument({ 
      margin: 50, 
      size: 'A4',
      bufferPages: true
    });
    
    const filename = `attendance_${startDate || 'all'}_to_${endDate || 'all'}_${Date.now()}.pdf`;

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);

    doc.pipe(res);

    // Add logo if available
    const logoPath = path.join(__dirname, '../assets/logo.jpeg');
    if (fs.existsSync(logoPath)) {
      doc.image(logoPath, 50, 45, { width: 100 });
    }

    doc.fontSize(20).text('Attendance Report', 200, 57, { align: 'center' });
    doc.moveDown();
    
    doc.fontSize(12).text(`Date Range: ${startDate || 'All'} to ${endDate || 'All'}`, { align: 'center' });
    doc.fontSize(12).text(`Total Records: ${attendance.length}`, { align: 'center' });
    doc.fontSize(10).text(`Generated: ${convertToIST(new Date()).format('YYYY-MM-DD HH:mm:ss IST')}`, { align: 'center' });
    doc.moveDown(2);

    const rowHeight = 30;
    let currentY = doc.y;

    // Header
    doc.fontSize(8).font('Helvetica-Bold');
    doc.text('Emp ID', 50, currentY, { width: 60 });
    doc.text('Name', 110, currentY, { width: 100 });
    doc.text('Date', 210, currentY, { width: 70 });
    doc.text('Time (IST)', 280, currentY, { width: 70 });
    doc.text('Location', 350, currentY, { width: 120 });
    
    currentY += rowHeight;
    doc.moveTo(50, currentY - 5).lineTo(550, currentY - 5).stroke();

    // Data rows
    doc.font('Helvetica');
    attendance.forEach((att, index) => {
      if (currentY > 700) {
        doc.addPage();
        currentY = 50;
        
        // Repeat header
        doc.font('Helvetica-Bold').fontSize(8);
        doc.text('Emp ID', 50, currentY, { width: 60 });
        doc.text('Name', 110, currentY, { width: 100 });
        doc.text('Date', 210, currentY, { width: 70 });
        doc.text('Time (IST)', 280, currentY, { width: 70 });
        doc.text('Location', 350, currentY, { width: 120 });
        currentY += rowHeight;
        doc.moveTo(50, currentY - 5).lineTo(550, currentY - 5).stroke();
        doc.font('Helvetica');
      }

      const istTime = convertToIST(att.timestamp).format('HH:mm:ss');
      const istDate = convertToIST(att.timestamp).format('YYYY-MM-DD');

      doc.fontSize(7);
      doc.text(att.employeeId || '', 50, currentY, { width: 60 });
      doc.text(att.userId ? att.userId.name : 'Unknown', 110, currentY, { width: 100 });
      doc.text(istDate, 210, currentY, { width: 70 });
      doc.text(istTime, 280, currentY, { width: 70 });
      doc.text(`${att.latitude.toFixed(4)}, ${att.longitude.toFixed(4)}`, 350, currentY, { width: 120 });
      
      currentY += rowHeight;
      
      if (index < attendance.length - 1) {
        doc.moveTo(50, currentY - 2).lineTo(550, currentY - 2).stroke('#CCCCCC');
      }
    });

    doc.end();

    console.log(`✅ PDF exported: ${filename}`);

  } catch (error) {
    console.error('Export PDF error:', error);
    if (!res.headersSent) {
      res.status(500).json({
        success: false,
        message: error.message
      });
    }
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
      .sort({ date: -1, timestamp: -1 })
      .limit(5000);

    const exportData = {
      exportDate: convertToIST(new Date()).format('YYYY-MM-DD HH:mm:ss'),
      timezone: 'Asia/Kolkata (IST)',
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
        date: convertToIST(att.timestamp).format('YYYY-MM-DD'),
        checkInTime: convertToIST(att.timestamp).format('HH:mm:ss'),
        latitude: att.latitude,
        longitude: att.longitude,
        timestamp: att.timestamp
      }))
    };

    const filename = `attendance_${Date.now()}.json`;
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).json(exportData);
  } catch (error) {
    console.error('Export JSON error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};