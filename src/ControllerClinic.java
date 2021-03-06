import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class ControllerClinic implements IController {

  ClinicStaff clinicStaff;

  private void addStaffInfo(Scanner scan, Connection conn, String username) {
    System.out.println("Please provide your employee number:");
    int empNo;
    empNo = scan.nextInt();

    scan.nextLine();
    System.out.println("Please provide your full name:");
    String fullName;
    fullName = scan.nextLine();

    String jobDesc = "";
    System.out.println("Would you like to add your job description?");

    boolean addJobDesc;
    String input = scan.next();
    scan.nextLine();
    switch (input) {
      case "yes":
      case "'y'":
      case "y":
      case "Yes":
      case "'Y'":
      case "Y":
        addJobDesc = true;
        System.out.println("You have indicated that you would like to add your job description.");
        System.out.println("Please enter it below:");
        jobDesc += scan.nextLine();
        break;
      case "no":
      case "'n'":
      case "n":
      case "No":
      case "'N'":
      case "N":
        addJobDesc = false;
        System.out.println("You have indicated that you would not like to add your job description.");
        break;
      default:
        System.out.println("Invalid option entered! Please try again.");
    }

    try {
      Statement statement = conn.createStatement();
      String stringToExecute = String.format(
          "INSERT INTO staff (emp_no, emp_name, user_id, job_description) "
              + "VALUES (%d, '%s', '%s', '%s')", empNo, fullName, username, jobDesc);
      statement.executeUpdate(stringToExecute);
    } catch (SQLException e) {
      System.out.println("ERROR: Could not add staff info to database.");
    }
  }

  private boolean staffUserExists(Scanner scan, String username, Connection conn) {
    try {
      CallableStatement staffWithUserExists = conn
          .prepareCall("{? = CALL check_clinic_staff_user_exists(?)}");
      staffWithUserExists.registerOutParameter(1, Types.BOOLEAN);
      staffWithUserExists.setString(2, username);
      staffWithUserExists.execute();
      if (!staffWithUserExists.getBoolean(1)) {
        this.addStaffInfo(scan, conn, username);
        return false;
      }
      return true;
    } catch (SQLException e) {
      System.out.println("ERROR: Could not fetch staff info to database.");
    }
    return false;
  }

  private Integer getUserClinic(String username, Connection conn) {
    try {
      CallableStatement getUserClinic = conn
          .prepareCall("{? = CALL get_staff_clinic(?)}");
      getUserClinic.registerOutParameter(1, Types.VARCHAR);
      getUserClinic.setString(2, username);
      getUserClinic.execute();
      return getUserClinic.getInt(1);
    } catch (SQLException e) {
      System.out.println("ERROR: Could not fetch staff info to database.");
    }
    return null;
  }

  @Override
  public void run(Scanner scan, Connection conn, String username) {

    boolean alreadyExisted = this.staffUserExists(scan, username, conn);

    ClinicStaff clinic;
    Integer selectedClinic;
    boolean userHasClinic; //= userHasClinic(username, conn);
    if (getUserClinic(username, conn) == 0) {
      userHasClinic = false;
    } else {
      userHasClinic = true;
    }

    if (!userHasClinic) {
      checkClinicLoop:
      while (true) {
        System.out.println("Please enter the clinic you work at:");
        try {
          selectedClinic = Integer.parseInt(scan.next());
        } catch (NumberFormatException e) {
          System.out.println("You have entered an invalid clinic id. Please try again.");
          continue;
        }
        try {
          String checkClinicExistsStr = "{? = CALL does_clinic_exist(?)}";
          CallableStatement checkClinicExists = conn.prepareCall(checkClinicExistsStr);
          checkClinicExists.registerOutParameter(1, Types.BOOLEAN);
          checkClinicExists.setInt(2, selectedClinic);
          checkClinicExists.execute();
          if (checkClinicExists.getBoolean(1)) {
            clinic = new ClinicStaff(username, conn, selectedClinic, alreadyExisted);
            clinicStaff = clinic;
            System.out.println("You have successfully joined clinic with id " + selectedClinic + ".");
            break checkClinicLoop;
          } else {
            System.out.println(
                "The clinic you have selected does not exist. Please try again.");
          }
        } catch (SQLException e) {
          System.out.println("ERROR: Could not select a clinic.");
        }
      }
    } else {
      clinic = new ClinicStaff(username, conn, getUserClinic(username, conn), alreadyExisted);
      clinicStaff = clinic;
    }

    while (true) {
      System.out.println("Option menu:");
      System.out.println("> Enter 1 to view all clinic information");
      System.out.println("> Enter 2 to add new available appointment");
      System.out.println("> Enter 3 to delete a patient from the system");
      System.out.println("> Enter 4 to view your upcoming appointments");
      System.out.println("> Enter 5 to log out");

      int choice = scan.nextInt();
      switch (choice) {
        case 1:
          try {
            clinic.getClinicInformation();
          } catch (Exception e) {
            System.out.println("Clinic information is not available!");
          }
          break;
        case 2:
          String dateTimeFormat = "yyyy-MM-dd HH:mm";
          String apptDateTime;
          scan.nextLine();
          while (true) {
            System.out.println("Please provide the appointment date and time (yyyy-MM-dd HH:mm):");
            apptDateTime = scan.nextLine();
            try {
              Date date = new SimpleDateFormat(dateTimeFormat).parse(apptDateTime);
              if (date.before(new Date())) {
                throw new IllegalArgumentException("You have provided an invalid appointment time. Please enter an appointment time in the future.");
              }
              break;
            } catch (Exception e) {
              System.out.println("You have provided an invalid appointment time. Please try again.");
            }
          }

          int empId = this.clinicStaff.getEmployeeId();

          this.clinicStaff.addAppointmentAvailability(apptDateTime + ":00", empId);
          break;
        case 3:
          System.out.println("Please provide the social security number (XXXXXXXXXX) of the patient you'd like to delete.");
          String ssn;
          while (true) {
            ssn = scan.next();
            for (int i = 0; i < ssn.length(); i++) {
              try {
                Integer.parseInt(String.valueOf(ssn.charAt(i)));
              } catch (NumberFormatException e) {
                System.out.println(
                    "Invalid social security number. SSN must be a 10-digit number (XXXXXXXXXX).\nPlease re-enter the patient's social security number:");
                continue;
              }
            }
            break;
          }
          this.clinicStaff.deletePatient(ssn);
          break;
        case 4:
          try {
            this.clinicStaff.getUpcomingAppointments();
          } catch (SQLException e) {
            System.out.println("ERROR: Could not retrieve upcoming appointments.");
          }
          break;
        case 5:
          try {
            clinicStaff.logOut(clinic.getCurrentSession(username));
            System.out.println("Successfully logged out!");
            System.exit(0);
          } catch (Exception e) {
            System.out.println("ERROR: Failed to log out!");
          }
          break;
        default:
          System.out.println("Invalid choice! Please try again.");
      }
    }


  }
}
