package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;
import java.util.Iterator;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    for (int i=0; i<16; i++) {
         fdt[i] = new FileDescriptor();
    }
    
    fdt[0].file = UserKernel.console.openForReading();

    fdt[1].file = UserKernel.console.openForWriting();  
    

    OpenFile file  = UserKernel.fileSystem.open("out", false);

    int fdi = -1; // File Descriptor index

    for (int i = 0; i < 16; i++) {
        if (fdt[i].file == null)
            fdi = i;
    }

    fdt[fdi].file = file;                                      /*@BAA*/
    fdt[fdi].position = 0;                                       /*@BAA*/


    pid = UserKernel.getNextPid();                                      /*@BCA*/

    /* register this new process in UserKernel's map                           */
    UserKernel.registerProcess(pid, this);   
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	//if (vaddr < 0 || vaddr >= memory.length)
	//    return 0;

	int vpn = Machine.processor().pageFromAddress(vaddr);
    int addressOffset = Machine.processor().offsetFromAddress(vaddr);

    TranslationEntry entry = null;
    entry = pageTable[vpn];
    entry.used = true;

    int ppn = entry.ppn;
    int paddr = (ppn*pageSize) + addressOffset;
    // check if physical page number is out of range
    if (ppn < 0 || ppn >= Machine.processor().getNumPhysPages())  {
        Lib.debug(dbgProcess,
                "\t\t UserProcess.readVirtualMemory(): bad ppn "+ppn);
        return 0;
    }         




    int amount = Math.min(length, memory.length-paddr);
	System.arraycopy(memory, paddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

    int vpn = Machine.processor().pageFromAddress(vaddr);
    int addressOffset = Machine.processor().offsetFromAddress(vaddr);

    TranslationEntry entry = null;
    entry = pageTable[vpn];
    entry.used = true;
    entry.dirty = true;

    int ppn = entry.ppn;
    int paddr = (ppn*pageSize) + addressOffset;

    if (entry.readOnly) {
        Lib.debug(dbgProcess,
                 "\t\t [UserProcess.writeVirtualMemory]: write read-only page "+ppn);
        return 0;
    }

    // check if physical page number is out of range
    if (ppn < 0 || ppn >= Machine.processor().getNumPhysPages())  {
        Lib.debug(dbgProcess, "\t\t [UserProcess.writeVirtualMemory]: bad ppn "+ppn);
        return 0;
    }


	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

    pageTable = new TranslationEntry[numPages];
    for (int i = 0; i < numPages; i++) {
        int ppn = UserKernel.getFreePage();
        pageTable[i] =  new TranslationEntry(i, ppn, true, false, false, false);
    } 

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

        TranslationEntry entry = pageTable[vpn];                                   /* @BBA */ 
        entry.readOnly = section.isReadOnly();                                     /* @BBA */ 
        int ppn = entry.ppn; 

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, ppn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < numPages; i++) {
            UserKernel.addFreePage(pageTable[i].ppn);
            pageTable[i].valid = false;
        } 
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }

    private int handleOpen(int name) {

        if (name < 0) {                                                      /*@BDA*/ 
            return -1;                                                     /*@BDA*/
        } 

        String filename = readVirtualMemoryString(name, 256);

        OpenFile file = ThreadedKernel.fileSystem.open(filename, false);

        if (file == null) {
            return -1;
        }

        int fdi = -1; // File Descriptor index

        for (int i = 0; i < 16; i++) {
            if (fdt[i].file == null)
                fdi = i;
        }

        if (fdi < 0) {
            return -1;
        }

        fdt[fdi].filename = filename;
        fdt[fdi].file = file;
        fdt[fdi].position = 0; 
        return fdi;
    }

    private int handleCreate(int name) {

        if (name < 0) {                                                      /*@BDA*/ 
            return -1;                                                     /*@BDA*/
        } 

        String filename = readVirtualMemoryString(name, 256);

        OpenFile file = ThreadedKernel.fileSystem.open(filename, true);

        if (file == null) {
            return -1;
        }

        int fdi = -1;

        for (int i = 0; i < 16; i++) {
            if (fdt[i].file == null)
                fdi = i;
        }

        if (fdi < 0) {
            return -1;
        }

        fdt[fdi].filename = filename;
        fdt[fdi].file = file;
        fdt[fdi].position = 0; 
        return fdi;
    }

    private int handleRead(int fileDescriptor, int bufferAddress, int count) {
        if (fileDescriptor < 0) return -1;
        if (fileDescriptor >= 16) return -1;
        if (fdt[fileDescriptor].file == null) return -1;

        FileDescriptor fd = fdt[fileDescriptor];
        byte[] buffer = new byte[count];

        int file = fd.file.read(fd.position, buffer, 0, count);

        if (file < 0) return -1;

        int n = writeVirtualMemory(bufferAddress, buffer);
        fd.position = fd.position + n;

        return file;
    }

    private int handleWrite(int fileDescriptor, int bufferAddress, int count) {
        if (fileDescriptor < 0) return -1;
        if (fileDescriptor >= 16) return -1;
        if (fdt[fileDescriptor].file == null) return -1;

        FileDescriptor fd = fdt[fileDescriptor];
        byte[] buffer = new byte[count];
        
        int n = readVirtualMemory(bufferAddress, buffer);
        
        int file = fd.file.write(fd.position, buffer, 0, n);

        if (file < 0) return -1;

        fd.position = fd.position + file;

        return file;
    }

    private int handleClose(int fileDescriptor) {
        if (fileDescriptor < 0) return -1;
        if (fileDescriptor >= 16) return -1;

        boolean file = true;

        FileDescriptor fd = fdt[fileDescriptor];

        fd.position = 0;
        fd.file.close();

        if (fd.toRemove) {
            file = ThreadedKernel.fileSystem.remove(fd.filename);
            fd.toRemove = false;
        }

        fd.filename = "";

        return file ? 0:-1;
    }

    private int handleUnlink(int name) {
        boolean file = true;

        String filename = readVirtualMemoryString(name, 256);

        int fdi = -1;

        for (int i = 0; i < 16; i++) {                   /* @BAA */
            if (fdt[i].filename == filename)                /* @BAA */
                fdi = i;                                   /* @BAA */
        }                                                   /* @BAA */

        if (fdi < 0) {
            file = ThreadedKernel.fileSystem.remove(filename);
        } else {
            fdt[fdi].toRemove = true;   
        }

        return file ? 0 : -1;
    }

    private void handleExit(int status) {

        for (int i = 0; i < 16; i++) {
            if (fdt[i].file != null)
                handleClose(i);
        }

        /* set any children of the process no longer have a parent process(null).*/ 
        while (children != null && !children.isEmpty())  {
            int childPid = children.removeFirst();
            UserProcess childProcess = UserKernel.getProcessByID(childPid);
            childProcess.ppid = 1;
        }

        /*  set the process's exit status to status that caller specifies(normal)* 
         *  or -1(exception)                                                     */
        this.exitStatus = status;
        Lib.debug(dbgProcess, "exitStatus: "+status);

        /* unloadSections and release memory pages                               */
        this.unloadSections();

        /* finish associated thread                                              */
        if (this.pid == 1) {
            Lib.debug(dbgProcess, "I am the root process");
            Kernel.kernel.terminate();
        }
        else {
            Lib.assertTrue(KThread.currentThread() == this.thread);
            KThread.currentThread().finish();
        }

        Lib.assertNotReached();
    }

    private int handleJoin(int processID, int status) {
        boolean childFlag = false;
        int tmp = 0;
        Iterator<Integer> it = this.children.iterator();
        while(it.hasNext()) {
            tmp = it.next();
            if (tmp == processID) {
                it.remove();
                childFlag = true;
                break;
            }
        }

        if (childFlag == false) {
            return -1;
        }

        UserProcess childProcess = UserKernel.getProcessByID(processID);

        if (childProcess == null) {
            return -2;
        }

        /* child process's thread joins current thread                           */
        childProcess.thread.join();

        /* we needn't the object of child process after invoking join,           */
        /* so unregister it in kernel's process map                              */  
        UserKernel.unregisterProcess(processID);

        /* store the exit status to status pointed by the second argument        */
        byte temp[] = new byte[4];
        temp=Lib.bytesFromInt(childProcess.exitStatus);
        int cntBytes = writeVirtualMemory(status, temp);
        
        return (cntBytes != 4)? 1:0;
    }

    private int handleExec(int file, int argc, int argv) {
        if (argc < 1) {
            return -1;
        }

        String filename = readVirtualMemoryString(file, 256);
        if (filename == null) {
            return -1;
        }

        /* filename doesn't have the ".coff" extension                            */
        String suffix = filename.substring(filename.length()-4, filename.length());
        if (!suffix.equals(".coff")) {
            return -1;
        }

        /* get args from address of argv                                          */  
        String args[] = new String[argc];
        byte   temp[] = new byte[4];
        for (int i = 0; i < argc; i++) {
            int cntBytes = readVirtualMemory(argv+i*4, temp);
            if (cntBytes != 4) {
                return -1;
            }

            int argAddress = Lib.bytesToInt(temp, 0);
            args[i] = readVirtualMemoryString(argAddress, 256);
        }

        /* create a new child process*/
        UserProcess childProcess = UserProcess.newUserProcess();
        childProcess.ppid = this.pid;
        this.children.add(childProcess.pid);
         
        /* invoke UserProcess.execute to load executable and create a new UThread */
        boolean f = childProcess.execute(filename, args);

        return f? childProcess.pid : -1;
    }




    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();

    case syscallOpen: 
        return handleOpen(a0);

    case syscallCreate: 
        return handleCreate(a0);

    case syscallRead: 
        return handleRead(a0, a1, a2);

    case syscallWrite:
        return handleWrite(a0, a1, a2);

    case syscallClose: 
        return handleClose(a0);

    case syscallUnlink: 
        return handleUnlink(a0);

    case syscallExec:
        return handleExec(a0, a1, a2);

    case syscallExit: 
        handleExit(a0);
        break;

    case syscallJoin:
        return handleJoin(a0, a1);

	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    public class FileDescriptor {
        public FileDescriptor() {
        }
        private  String   filename = "";
        private  OpenFile file = null;
        private int position = 0;
        private boolean toRemove = false;
    } 

    private FileDescriptor fdt[] = new FileDescriptor[16];

     private int cntOpenedFiles = 0;

    /* process ID                                                       */
    private int pid;

    /* parent process's ID                                              */
    private int ppid;

    /* child processes                                                  */
    private LinkedList<Integer> children
                   = new LinkedList<Integer>();

    /* exit status                                                      */
    private int exitStatus;

    /* user thread that's associated with this process                  */
    private UThread thread;

}
