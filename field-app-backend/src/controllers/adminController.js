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
    // Fix backslashes in URL and ensure proper URL format
    let cleanUrl = url.replace(/\\/g, '/');
    
    // If URL is a relative path (starts with uploads/), prepend the base URL
    if (cleanUrl.startsWith('uploads/')) {
      // Get the base URL from environment or use default
      const baseUrl = process.env.BASE_URL || 'https://asap-kc7n.onrender.com';
      cleanUrl = `${baseUrl}/${cleanUrl}`;
    }
    
    console.log(`Downloading: ${cleanUrl}`);
    
    const response = await axios({
      method: 'GET',
      url: cleanUrl,
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
// In your admin attendance query
exports.getAllAttendance = async (req, res) => {
  try {
    const { startDate, endDate, employeeId } = req.query;
    
    let query = {};
    
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }
    
    if (employeeId) {
      query.employeeId = employeeId;
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ timestamp: -1 });

    // Address is already in the attendance records
    res.status(200).json({
      success: true,
      count: attendance.length,
      data: attendance // Address field included automatically
    });
  } catch (error) {
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
  // Move temp directory OUTSIDE src to avoid nodemon restarts
  const tempDir = path.join(__dirname, '../../temp');
  const userTempDir = path.join(tempDir, `user_${req.params.userId}_${Date.now()}`);

  try {
    const { startDate, endDate } = req.body;
    const userId = req.params.userId;

    console.log('Export request:', { userId, startDate, endDate });

    // Set aggressive timeouts
    req.setTimeout(0); // No timeout
    res.setTimeout(0);

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
      .limit(500); // Reduced limit for stability

    console.log(`Found ${attendance.length} attendance records for export`);

    if (attendance.length === 0) {
      fs.rmSync(userTempDir, { recursive: true, force: true });
      return res.status(404).json({
        success: false,
        message: 'No attendance records found for the selected date range'
      });
    }

    // ✅ Create CSV with Address column
    let csvContent = 'Date,Check-in Time,Latitude,Longitude,Address,Selfie Filename\n';
    
    const selfiesDirectory = path.join(userTempDir, 'selfies');
    fs.mkdirSync(selfiesDirectory, { recursive: true });

    // Download images sequentially (not concurrently)
    let downloadedCount = 0;
    for (let i = 0; i < Math.min(attendance.length, 100); i++) { // Limit to first 100
      const att = attendance[i];
      const filename = `selfie_${att.date}_${att.checkInTime.replace(/:/g, '-')}.jpg`;
      const filepath = path.join(selfiesDirectory, filename);

      // ✅ Added address field to CSV
      csvContent += `"${att.date}","${att.checkInTime}",${att.latitude},${att.longitude},"${att.address || 'Address unavailable'}","selfies/${filename}"\n`;

      // Download images for better data
      if (att.selfiePath) {
        try {
          const success = await downloadImage(att.selfiePath, filepath);
          if (success) {
            downloadedCount++;
            console.log(`Downloaded ${downloadedCount} of ${Math.min(attendance.length, 100)}`);
          }
        } catch (err) {
          console.error(`Failed to download ${filename}:`, err.message);
        }
      }
    }
    
    // Add remaining records to CSV without downloading images
    for (let i = 100; i < attendance.length; i++) {
      const att = attendance[i];
      const filename = `selfie_${att.date}_${att.checkInTime.replace(/:/g, '-')}.jpg`;
      // ✅ Added address field to CSV
      csvContent += `"${att.date}","${att.checkInTime}",${att.latitude},${att.longitude},"${att.address || 'Address unavailable'}","${att.selfiePath}"\n`;
    }

    console.log(`Downloaded ${downloadedCount} selfie images`);

    // Write CSV
    const csvFilePath = path.join(userTempDir, `${user.employeeId}_attendance.csv`);
    fs.writeFileSync(csvFilePath, csvContent);
    console.log(`CSV written: ${csvFilePath}, size: ${fs.statSync(csvFilePath).size} bytes`);

    // Write user info
    const userInfoPath = path.join(userTempDir, 'user_info.json');
    const userInfo = JSON.stringify({
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
      downloadedSelfies: downloadedCount,
      exportDate: new Date().toISOString()
    }, null, 2);
    fs.writeFileSync(userInfoPath, userInfo);
    console.log(`User info written: ${userInfoPath}`);

    // Verify files exist
    const allFiles = [];
    const walkDir = (dir) => {
      const files = fs.readdirSync(dir);
      files.forEach(file => {
        const filePath = path.join(dir, file);
        const stat = fs.statSync(filePath);
        if (stat.isDirectory()) {
          walkDir(filePath);
        } else {
          allFiles.push({ path: filePath, size: stat.size });
        }
      });
    };
    walkDir(userTempDir);
    console.log(`Total files to archive: ${allFiles.length}`);
    console.log(`Total size: ${allFiles.reduce((sum, f) => sum + f.size, 0)} bytes`);

    // Create ZIP with proper settings
    const archive = archiver('zip', { 
      zlib: { level: 6 }, // Balanced compression
      statConcurrency: 1 // Process files one at a time
    });
    
    const zipFilename = `${user.employeeId}_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Content-Transfer-Encoding', 'binary');

    // Critical: Handle errors before piping
    let archiveFinalized = false;
    
    archive.on('error', (err) => {
      console.error('Archive error:', err);
      archiveFinalized = true;
      try {
        if (fs.existsSync(userTempDir)) {
          fs.rmSync(userTempDir, { recursive: true, force: true });
        }
      } catch (e) {}
      
      if (!res.headersSent) {
        res.status(500).json({ success: false, message: err.message });
      } else {
        res.end();
      }
    });

    archive.on('warning', (err) => {
      if (err.code !== 'ENOENT') {
        console.warn('Archive warning:', err);
      }
    });

    archive.on('end', () => {
      console.log(`Archive data has been flushed`);
    });

    // Pipe archive to response
    archive.pipe(res);

    // Create a root folder name for the ZIP contents
    const rootFolderName = `${user.employeeId}_export_${moment().format('YYYY-MM-DD')}`;

    console.log(`Adding files to archive under folder: ${rootFolderName}`);

    // Add CSV file
    const csvArchivePath = path.join(userTempDir, `${user.employeeId}_attendance.csv`);
    if (fs.existsSync(csvArchivePath)) {
      archive.file(csvArchivePath, { name: `${rootFolderName}/${user.employeeId}_attendance.csv` });
    }

    // Add user info
    const userInfoArchivePath = path.join(userTempDir, 'user_info.json');
    if (fs.existsSync(userInfoArchivePath)) {
      archive.file(userInfoArchivePath, { name: `${rootFolderName}/user_info.json` });
    }

    // Add selfies directory
    const selfiesArchiveDir = path.join(userTempDir, 'selfies');
    if (fs.existsSync(selfiesArchiveDir)) {
      const selfies = fs.readdirSync(selfiesArchiveDir);
      console.log(`Adding ${selfies.length} selfies to archive`);
      
      for (const selfie of selfies) {
        const selfiePath = path.join(selfiesArchiveDir, selfie);
        archive.file(selfiePath, { name: `${rootFolderName}/selfies/${selfie}` });
      }
    }

    // Finalize the archive
    await archive.finalize();
    archiveFinalized = true;

    console.log(`✅ Export completed: ${zipFilename}, size: ${archive.pointer()} bytes`);

    // Wait for response to finish sending before cleanup
    await new Promise((resolve) => {
      res.on('finish', () => {
        console.log('Response stream finished');
        resolve();
      });
      res.on('error', (err) => {
        console.error('Response error:', err);
        resolve();
      });
    });

    // Cleanup after a short delay
    setTimeout(() => {
      try {
        if (fs.existsSync(userTempDir)) {
          fs.rmSync(userTempDir, { recursive: true, force: true });
          console.log(`Cleaned up: ${userTempDir}`);
        }
      } catch (cleanupErr) {
        console.error('Cleanup error:', cleanupErr);
      }
    }, 3000);

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
  // Move temp directory OUTSIDE src to avoid nodemon restarts
  const tempDir = path.join(__dirname, '../../temp');
  const exportTempDir = path.join(tempDir, `all_export_${Date.now()}`);

  try {
    const { startDate, endDate } = req.body;

    console.log('Export all request:', { startDate, endDate });

    // Remove timeouts
    req.setTimeout(0);
    res.setTimeout(0);

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

    // Limit to prevent memory issues
    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(1000); // Reduced from 2000

    console.log(`Found ${attendance.length} total records for export`);

    if (attendance.length === 0) {
      fs.rmSync(exportTempDir, { recursive: true, force: true });
      return res.status(404).json({
        success: false,
        message: 'No attendance records found for the selected date range'
      });
    }

    // ✅ Create CSV with Address column
    let csvContent = 'Employee ID,Employee Name,Department,Designation,Date,Check-in Time,Latitude,Longitude,Address,Selfie URL\n';
    
    attendance.forEach(att => {
      csvContent += `"${att.employeeId}",`;
      csvContent += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csvContent += `"${att.userId ? att.userId.department : ''}",`;
      csvContent += `"${att.userId ? att.userId.designation : ''}",`;
      csvContent += `"${att.date}",`;
      csvContent += `"${att.checkInTime}",`;
      csvContent += `${att.latitude},`;
      csvContent += `${att.longitude},`;
      csvContent += `"${att.address || 'Address unavailable'}",`; // ✅ Added address field
      csvContent += `"${att.selfiePath}"\n`;
    });

    const csvPath = path.join(exportTempDir, 'all_attendance.csv');
    fs.writeFileSync(csvPath, csvContent);

    const summaryPath = path.join(exportTempDir, 'summary.json');
    fs.writeFileSync(summaryPath, JSON.stringify({
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      exportDate: new Date().toISOString(),
      note: 'Selfie URLs are included in CSV. Download individual user exports for actual image files.'
    }, null, 2));

    // Create ZIP
    const archive = archiver('zip', { 
      zlib: { level: 6 },
      statConcurrency: 1
    });
    
    const zipFilename = `all_data_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Content-Transfer-Encoding', 'binary');

    let archiveFinalized = false;

    archive.on('error', (err) => {
      console.error('Archive error:', err);
      archiveFinalized = true;
      try {
        if (fs.existsSync(exportTempDir)) {
          fs.rmSync(exportTempDir, { recursive: true, force: true });
        }
      } catch (e) {}
      
      if (!res.headersSent) {
        res.status(500).json({ success: false, message: err.message });
      } else {
        res.end();
      }
    });

    archive.on('end', () => {
      console.log('Archive data has been flushed');
    });

    archive.pipe(res);

    // Create a root folder name for the ZIP contents
    const rootFolderName = `all_data_export_${moment().format('YYYY-MM-DD')}`;

    // Add files individually with root folder structure
    const files = fs.readdirSync(exportTempDir);
    console.log(`Adding ${files.length} files to archive under folder: ${rootFolderName}`);

    for (const file of files) {
      const filePath = path.join(exportTempDir, file);
      archive.file(filePath, { name: `${rootFolderName}/${file}` });
    }

    await archive.finalize();
    archiveFinalized = true;

    console.log(`✅ Export all completed: ${zipFilename}, size: ${archive.pointer()} bytes`);

    // Wait for response to finish
    await new Promise((resolve) => {
      res.on('finish', () => {
        console.log('Response stream finished');
        resolve();
      });
      res.on('error', (err) => {
        console.error('Response error:', err);
        resolve();
      });
    });

    // Cleanup after delay
    setTimeout(() => {
      try {
        if (fs.existsSync(exportTempDir)) {
          fs.rmSync(exportTempDir, { recursive: true, force: true });
          console.log(`Cleaned up: ${exportTempDir}`);
        }
      } catch (cleanupErr) {
        console.error('Cleanup error:', cleanupErr);
      }
    }, 3000);

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

    // ✅ Added Address column
    let csv = 'Employee ID,Employee Name,Department,Designation,Date,Check-in Time,Latitude,Longitude,Address,Selfie URL\n';
    
    attendance.forEach(att => {
      csv += `"${att.employeeId}",`;
      csv += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csv += `"${att.userId ? att.userId.department : ''}",`;
      csv += `"${att.userId ? att.userId.designation : ''}",`;
      csv += `"${att.date}",`;
      csv += `"${att.checkInTime}",`;
      csv += `${att.latitude},`;
      csv += `${att.longitude},`;
      csv += `"${att.address || 'Address unavailable'}",`; // ✅ Added address field
      csv += `"${att.selfiePath}"\n`;
    });

    const filename = `attendance_${startDate || 'all'}_to_${endDate || 'all'}_${Date.now()}.csv`;
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).send(csv);

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

    // ✅ Use landscape orientation for wider table with address
    const doc = new PDFDocument({ 
      margin: 30, 
      size: 'A4',
      layout: 'landscape', // ✅ Changed to landscape for address column
      bufferPages: true
    });
    
    const filename = `attendance_${startDate || 'all'}_to_${endDate || 'all'}_${Date.now()}.pdf`;

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);

    doc.pipe(res);

    doc.fontSize(20).text('Attendance Report', { align: 'center' });
    doc.moveDown();
    
    doc.fontSize(12).text(`Date Range: ${startDate || 'All'} to ${endDate || 'All'}`, { align: 'center' });
    doc.fontSize(12).text(`Total Records: ${attendance.length}`, { align: 'center' });
    doc.fontSize(10).text(`Generated: ${new Date().toLocaleString()}`, { align: 'center' });
    doc.moveDown(2);

    const rowHeight = 30; // Increased for address wrapping
    let currentY = doc.y;

    // ✅ Updated column headers with address
    doc.fontSize(8).font('Helvetica-Bold');
    doc.text('Emp ID', 30, currentY, { width: 50 });
    doc.text('Name', 85, currentY, { width: 80 });
    doc.text('Date', 170, currentY, { width: 60 });
    doc.text('Time', 235, currentY, { width: 45 });
    doc.text('Location', 285, currentY, { width: 80 });
    doc.text('Address', 370, currentY, { width: 400 }); // ✅ Added address column
    
    currentY += rowHeight;
    doc.moveTo(30, currentY - 5).lineTo(780, currentY - 5).stroke();

    doc.font('Helvetica');
    attendance.forEach((att, index) => {
      if (currentY > 520) { // Adjusted for landscape
        doc.addPage();
        currentY = 50;
        
        doc.font('Helvetica-Bold').fontSize(8);
        doc.text('Emp ID', 30, currentY, { width: 50 });
        doc.text('Name', 85, currentY, { width: 80 });
        doc.text('Date', 170, currentY, { width: 60 });
        doc.text('Time', 235, currentY, { width: 45 });
        doc.text('Location', 285, currentY, { width: 80 });
        doc.text('Address', 370, currentY, { width: 400 });
        currentY += rowHeight;
        doc.moveTo(30, currentY - 5).lineTo(780, currentY - 5).stroke();
        doc.font('Helvetica');
      }

      doc.fontSize(7);
      doc.text(att.employeeId || '', 30, currentY, { width: 50 });
      doc.text(att.userId ? att.userId.name : 'Unknown', 85, currentY, { width: 80 });
      doc.text(att.date, 170, currentY, { width: 60 });
      doc.text(att.checkInTime, 235, currentY, { width: 45 });
      doc.text(`${att.latitude.toFixed(4)}, ${att.longitude.toFixed(4)}`, 285, currentY, { width: 80 });
      // ✅ Added address with word wrapping
      doc.text(att.address || 'Address unavailable', 370, currentY, { 
        width: 400,
        height: rowHeight - 5,
        ellipsis: true
      });
      
      currentY += rowHeight;
      
      if (index < attendance.length - 1) {
        doc.moveTo(30, currentY - 2).lineTo(780, currentY - 2).stroke('#CCCCCC');
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
        address: att.address || 'Address unavailable', // ✅ Added address field
        selfieUrl: att.selfiePath,
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